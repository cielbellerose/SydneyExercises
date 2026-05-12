package org.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {

    private static final CSVFormat CSV_FORMAT = CSVFormat.Builder.create(CSVFormat.RFC4180)
        .setHeader()
        .setSkipHeaderRecord(true)
        .setAllowDuplicateHeaderNames(false)
        .build();

    private static final String PATH_TO_CSV =
        "/Users/kinseybellerose/Desktop/RealEstateServer/REDataLoader/src/main/java/org/example/nsw_property_data.csv";

    // SQLite creates this file automatically on first connection — no separate "create database" step.
    private static final String DB_URL =
        "jdbc:sqlite:/Users/kinseybellerose/Desktop/RealEstateServer/realestate.db";

    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS property (" +
        "  property_id        INTEGER PRIMARY KEY," +
        "  download_date      TEXT," +
        "  council_name       TEXT," +
        "  purchase_price     INTEGER," +
        "  address            TEXT," +
        "  post_code          TEXT," +
        "  property_type      TEXT," +
        "  strata_lot_number  TEXT," +
        "  property_name      TEXT," +
        "  area               REAL," +
        "  area_type          TEXT," +
        "  contract_date      TEXT," +
        "  settlement_date    TEXT," +
        "  zoning             TEXT," +
        "  nature_of_property TEXT," +
        "  primary_purpose    TEXT," +
        "  legal_description  TEXT," +
        // SQLite has no boolean type; we use 0/1 in an INTEGER column.
        "  for_sale           INTEGER NOT NULL DEFAULT 0" +
        ")";

    // INSERT OR REPLACE = if a row with the same property_id already exists, overwrite it.
    // Makes the loader idempotent — safe to rerun without UNIQUE constraint errors.
    private static final String INSERT_SQL =
        "INSERT OR REPLACE INTO property (" +
        "  property_id, download_date, council_name, purchase_price, address," +
        "  post_code, property_type, strata_lot_number, property_name, area," +
        "  area_type, contract_date, settlement_date, zoning, nature_of_property," +
        "  primary_purpose, legal_description" +
        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    // How many rows to buffer in JDBC before flushing to SQLite. Bigger = fewer round-trips
    // but more JVM memory. 10k is a sweet spot for this dataset.
    private static final int BATCH_SIZE = 10_000;

    public static void main(String[] args) {
        final Path csvFilePath = Paths.get(PATH_TO_CSV);
        long startTime = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            applyBulkLoadPragmas(conn);
            createSchema(conn);
            clearTable(conn);

            // insert csv to db
            int inserted = bulkInsert(conn, csvFilePath);

            createIndexes(conn);

            long elapsedMs = System.currentTimeMillis() - startTime;
            System.out.printf("Loaded %,d records in %.2f seconds%n",
                              inserted, elapsedMs / 1000.0);
        } catch (SQLException | IOException e) {
            System.err.println("Load failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Trade durability for speed during the one-shot load. If it crashes, rerun.
    private static void applyBulkLoadPragmas(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode = OFF");   // no rollback journal
            s.execute("PRAGMA synchronous = OFF");    // skip fsync after writes
        }
    }

    private static void createSchema(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute(CREATE_TABLE_SQL);
        }
    }

    // Drop indexes before inserting: maintaining indexes during a 4.85M-row insert is
    // much slower than building them once at the end against the finished table.
    private static void clearTable(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("DELETE FROM property");
            s.execute("DROP INDEX IF EXISTS idx_property_postcode");
            s.execute("DROP INDEX IF EXISTS idx_property_price");
        }
    }

    private static int bulkInsert(Connection conn, Path csvFilePath)
            throws SQLException, IOException {
        // Turn off auto-commit so the entire load is one transaction. Without this,
        // every INSERT would be its own transaction with its own disk sync — orders of magnitude slower.
        conn.setAutoCommit(false);
        int inserted = 0;
        int skipped = 0;

        try (CSVParser parser = CSVParser.parse(csvFilePath, StandardCharsets.UTF_8, CSV_FORMAT);
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            for (CSVRecord rec : parser) {
                try {
                    bindRecord(ps, rec);
                    ps.addBatch();
                    inserted++;

                    // Flush in chunks so the JDBC batch buffer doesn't grow unbounded.
                    if (inserted % BATCH_SIZE == 0) {
                        ps.executeBatch();
                    }
                } catch (NumberFormatException e) {
                    // Row had unparseable numeric field (e.g. property_id missing). Skip it.
                    skipped++;
                }
            }
            ps.executeBatch();   // flush whatever is left in the last partial batch
            conn.commit();       // commit the single big transaction
        } catch (SQLException | IOException e) {
            conn.rollback();     // any failure → throw out the whole load, leave DB unchanged
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }

        if (skipped > 0) {
            System.out.printf("Skipped %,d malformed rows%n", skipped);
        }
        return inserted;
    }

    // Binds one CSV row's fields to the 17 "?" placeholders in INSERT_SQL.
    private static void bindRecord(PreparedStatement ps, CSVRecord rec) throws SQLException {
        ps.setInt(1,    Integer.parseInt(rec.get("property_id").trim()));
        ps.setString(2, nullIfBlank(rec.get("download_date")));
        ps.setString(3, nullIfBlank(rec.get("council_name")));
        setIntOrNull(ps, 4, rec.get("purchase_price"));
        ps.setString(5, nullIfBlank(rec.get("address")));
        ps.setString(6, nullIfBlank(rec.get("post_code")));
        ps.setString(7, nullIfBlank(rec.get("property_type")));
        ps.setString(8, nullIfBlank(rec.get("strata_lot_number")));
        ps.setString(9, nullIfBlank(rec.get("property_name")));
        setRealOrNull(ps, 10, rec.get("area"));
        ps.setString(11, nullIfBlank(rec.get("area_type")));
        ps.setString(12, nullIfBlank(rec.get("contract_date")));
        ps.setString(13, nullIfBlank(rec.get("settlement_date")));
        ps.setString(14, nullIfBlank(rec.get("zoning")));
        ps.setString(15, nullIfBlank(rec.get("nature_of_property")));
        ps.setString(16, nullIfBlank(rec.get("primary_purpose")));
        ps.setString(17, nullIfBlank(rec.get("legal_description")));
    }

    private static void setIntOrNull(PreparedStatement ps, int idx, String s) throws SQLException {
        if (s == null || s.isBlank()) {
            ps.setNull(idx, java.sql.Types.INTEGER);
        } else {
            ps.setLong(idx, Long.parseLong(s.trim()));
        }
    }

    private static void setRealOrNull(PreparedStatement ps, int idx, String s) throws SQLException {
        if (s == null || s.isBlank()) {
            ps.setNull(idx, java.sql.Types.REAL);
        } else {
            ps.setDouble(idx, Double.parseDouble(s.trim()));
        }
    }

    // Empty CSV cells become SQL NULL, not empty strings. Cleaner queries downstream.
    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // Build indexes once, against the finished table. Much faster than maintaining
    // them during 4.85M individual INSERTs.
    private static void createIndexes(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            System.out.println("Creating indexes...");
            s.execute("CREATE INDEX IF NOT EXISTS idx_property_postcode ON property(post_code)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_property_price ON property(purchase_price)");
        }
    }
}
