package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MarkupLoader {

  private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
  private static final String DB_USER = "realestate";
  private static final String DB_PASS = "realestate";

  private static final int TOTAL_LISTINGS = 1_000;
  private static final double MARKUP = 1.20;  // 20% above last sale price
  private static final int BATCH_SIZE = 500;

  public static void main(String[] args) throws Exception {
    Random rnd = new Random(42);
    long start = System.currentTimeMillis();

    List<long[]> properties = loadCandidateProperties();
    System.out.printf("Loaded %,d candidate properties from property table%n", properties.size());
    if (properties.isEmpty()) {
      System.err.println("No properties found — load property data first.");
      return;
    }
    if (properties.size() < TOTAL_LISTINGS) {
      System.err.printf("Only %,d candidates available, requested %,d — will create %d listings.%n",
              properties.size(), TOTAL_LISTINGS, properties.size());
    }

    // Partial Fisher-Yates shuffle: pick TOTAL_LISTINGS distinct properties without
    // duplicates. Faster than ORDER BY RANDOM() on a multi-million-row table.
    int target = Math.min(TOTAL_LISTINGS, properties.size());
    for (int i = 0; i < target; i++) {
      int j = i + rnd.nextInt(properties.size() - i);
      long[] tmp = properties.get(i);
      properties.set(i, properties.get(j));
      properties.set(j, tmp);
    }

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
      conn.setAutoCommit(false);

      try (PreparedStatement insListing = conn.prepareStatement(
              "INSERT INTO listing(property_id, listed_date, status) " +
                      "VALUES (?, ?, 'active') " +
                      "RETURNING listing_id");
           PreparedStatement insPrice = conn.prepareStatement(
                   "INSERT INTO listing_price(listing_id, price, effective_date) " +
                           "VALUES (?, ?, ?)")) {

        String today = LocalDate.now().toString();
        int created = 0;
        int pendingPrices = 0;

        for (int i = 0; i < target; i++) {
          long propertyId = properties.get(i)[0];
          long lastPrice = properties.get(i)[1];
          long newPrice = Math.round(lastPrice * MARKUP);

          insListing.setLong(1, propertyId);
          insListing.setString(2, today);

          long listingId;
          try (ResultSet rs = insListing.executeQuery()) {
            if (!rs.next()) continue;
            listingId = rs.getLong(1);
          }

          insPrice.setLong(1, listingId);
          insPrice.setLong(2, newPrice);
          insPrice.setString(3, today);
          insPrice.addBatch();
          pendingPrices++;
          created++;

          if (pendingPrices >= BATCH_SIZE) {
            insPrice.executeBatch();
            pendingPrices = 0;
          }

          if (created % 100 == 0) {
            System.out.printf("Progress: %,d listings created%n", created);
          }
        }

        if (pendingPrices > 0) insPrice.executeBatch();
        conn.commit();

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Done — %,d listings created in %.2fs%n",
                created, elapsed / 1000.0);
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Loads all properties with a valid purchase price.
   * Returns a list of [property_id, purchase_price] pairs.
   */
  private static List<long[]> loadCandidateProperties() throws Exception {
    List<long[]> out = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery(
                 "SELECT property_id, purchase_price FROM property " +
                         "WHERE purchase_price IS NOT NULL AND purchase_price > 0")) {
      while (rs.next()) {
        out.add(new long[] { rs.getLong(1), rs.getLong(2) });
      }
    }
    return out;
  }
}