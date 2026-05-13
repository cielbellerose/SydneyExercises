package org.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public final class PropertyLoader {

    private PropertyLoader() {}

    private static final CSVFormat CSV_FORMAT = CSVFormat.Builder.create(CSVFormat.RFC4180)
        .setHeader()
        .setSkipHeaderRecord(true)
        .setAllowDuplicateHeaderNames(false)
        .build();

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

    public static int load(Connection conn, Path csvFilePath) throws SQLException, IOException {
        clearTable(conn);
        return bulkInsert(conn, csvFilePath);
    }

    private static void clearTable(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("DELETE FROM property");
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
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setLong(idx, Long.parseLong(s.trim()));
        }
    }

    private static void setRealOrNull(PreparedStatement ps, int idx, String s) throws SQLException {
        if (s == null || s.isBlank()) {
            ps.setNull(idx, Types.REAL);
        } else {
            ps.setDouble(idx, Double.parseDouble(s.trim()));
        }
    }

    // Empty CSV cells become SQL NULL, not empty strings. Cleaner queries downstream.
    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
