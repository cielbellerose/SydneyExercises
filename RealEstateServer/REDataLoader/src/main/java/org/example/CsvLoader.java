package org.example;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CsvLoader {

  private CsvLoader() {}

  private static final String CREATE_STAGE_SQL =
          "CREATE TEMP TABLE property_stage (" +
                  "  property_id TEXT, download_date TEXT, council_name TEXT, purchase_price TEXT," +
                  "  address TEXT, post_code TEXT, property_type TEXT, strata_lot_number TEXT," +
                  "  property_name TEXT, area TEXT, area_type TEXT, contract_date TEXT," +
                  "  settlement_date TEXT, zoning TEXT, nature_of_property TEXT," +
                  "  primary_purpose TEXT, legal_description TEXT" +
                  ") ON COMMIT DROP";

  private static final String COPY_SQL =
          "COPY property_stage FROM STDIN WITH (FORMAT csv, HEADER true)";

  // DISTINCT ON + ORDER BY ctid DESC = last occurrence of each property_id in the CSV wins.
  // Rows with non-integer property_id are filtered out; bad purchase_price / area become NULL.
  // ON CONFLICT DO UPDATE refreshes existing rows in place — listings/listing_price keep their
  // FK references rather than being cascade-deleted on reload. for_sale is intentionally not
  // touched so manually-flipped flags survive a reload.
  private static final String INSERT_SQL =
          "INSERT INTO property (" +
                  "  property_id, download_date, council_name, purchase_price, address," +
                  "  post_code, property_type, strata_lot_number, property_name, area," +
                  "  area_type, contract_date, settlement_date, zoning, nature_of_property," +
                  "  primary_purpose, legal_description" +
                  ") " +
                  "SELECT DISTINCT ON (BTRIM(property_id)::INTEGER) " +
                  "  BTRIM(property_id)::INTEGER," +
                  "  NULLIF(download_date, '')," +
                  "  NULLIF(council_name, '')," +
                  "  CASE WHEN BTRIM(purchase_price) ~ '^-?[0-9]+$' THEN BTRIM(purchase_price)::BIGINT END," +
                  "  NULLIF(address, '')," +
                  "  NULLIF(post_code, '')," +
                  "  NULLIF(property_type, '')," +
                  "  NULLIF(strata_lot_number, '')," +
                  "  NULLIF(property_name, '')," +
                  "  CASE WHEN BTRIM(area) ~ '^-?[0-9]+(\\.[0-9]+)?$' THEN BTRIM(area)::DOUBLE PRECISION END," +
                  "  NULLIF(area_type, '')," +
                  "  NULLIF(contract_date, '')," +
                  "  NULLIF(settlement_date, '')," +
                  "  NULLIF(zoning, '')," +
                  "  NULLIF(nature_of_property, '')," +
                  "  NULLIF(primary_purpose, '')," +
                  "  NULLIF(legal_description, '') " +
                  "FROM property_stage " +
                  "WHERE BTRIM(property_id) ~ '^[0-9]+$' " +
                  "ORDER BY BTRIM(property_id)::INTEGER, ctid DESC " +
                  "ON CONFLICT (property_id) DO UPDATE SET " +
                  "  download_date = EXCLUDED.download_date," +
                  "  council_name = EXCLUDED.council_name," +
                  "  purchase_price = EXCLUDED.purchase_price," +
                  "  address = EXCLUDED.address," +
                  "  post_code = EXCLUDED.post_code," +
                  "  property_type = EXCLUDED.property_type," +
                  "  strata_lot_number = EXCLUDED.strata_lot_number," +
                  "  property_name = EXCLUDED.property_name," +
                  "  area = EXCLUDED.area," +
                  "  area_type = EXCLUDED.area_type," +
                  "  contract_date = EXCLUDED.contract_date," +
                  "  settlement_date = EXCLUDED.settlement_date," +
                  "  zoning = EXCLUDED.zoning," +
                  "  nature_of_property = EXCLUDED.nature_of_property," +
                  "  primary_purpose = EXCLUDED.primary_purpose," +
                  "  legal_description = EXCLUDED.legal_description";

  public static int load(Connection conn, Path csvFilePath) throws SQLException, IOException {
    conn.setAutoCommit(false);
    try {
      try (Statement s = conn.createStatement()) {
        s.execute(CREATE_STAGE_SQL);
      }

      long staged;
      CopyManager copy = conn.unwrap(PGConnection.class).getCopyAPI();
      try (Reader r = Files.newBufferedReader(csvFilePath, StandardCharsets.UTF_8)) {
        staged = copy.copyIn(COPY_SQL, r);
      }

      int inserted;
      try (Statement s = conn.createStatement()) {
        inserted = s.executeUpdate(INSERT_SQL);
      }

      conn.commit();

      long skipped = staged - inserted;
      if (skipped > 0) {
        System.out.printf("Skipped %,d duplicate or malformed rows%n", skipped);
      }
      return inserted;
    } catch (SQLException | IOException e) {
      conn.rollback();
      throw e;
    } finally {
      conn.setAutoCommit(true);
    }
  }
}