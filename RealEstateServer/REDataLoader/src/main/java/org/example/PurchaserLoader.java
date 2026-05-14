package org.example;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PurchaserLoader {

  private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
  private static final String DB_USER = "realestate";
  private static final String DB_PASS = "realestate";

  private static final int TOTAL_PURCHASERS = 10_000;
  private static final int MAX_POSTCODES = 5;
  private static final int BATCH_SIZE = 500;

  private static final String[] FIRST_NAMES = {
          "Aiden", "Olivia", "Liam", "Emma", "Noah", "Ava", "Ethan", "Sophia",
          "Mason", "Isabella", "Lucas", "Mia", "Logan", "Charlotte", "Jack",
          "Amelia", "Oliver", "Harper", "Leo", "Evelyn", "Henry", "Ella",
          "Sebastian", "Grace", "Daniel", "Chloe", "Matthew", "Zoe", "James", "Lily"
  };

  private static final String[] LAST_NAMES = {
          "Smith", "Jones", "Brown", "Taylor", "Wilson", "Lee", "Patel", "Nguyen",
          "Kim", "Chen", "Garcia", "Singh", "Walker", "Wright", "King", "Scott",
          "Green", "Baker", "Hall", "Adams", "Murphy", "Clarke", "Reed", "Cooper",
          "Bailey", "Bell", "Cox", "Howard", "Ward", "Foster"
  };

  public static void main(String[] args) throws Exception {
    Random rnd = new Random(42); // fixed seed = reproducible runs
    long start = System.currentTimeMillis();

    List<String> postcodes = loadPostcodes();
    System.out.printf("Loaded %d distinct postcodes from property table%n", postcodes.size());
    if (postcodes.isEmpty()) {
      System.err.println("No postcodes found — load property data first.");
      return;
    }

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
      conn.setAutoCommit(false);

      try (PreparedStatement insPurchaser = conn.prepareStatement(
              "INSERT INTO purchaser(name, email, created_at) VALUES (?, ?, DATE(?)) " +
                      "ON CONFLICT (email) DO NOTHING " +
                      "RETURNING purchaser_id");
           PreparedStatement insInterest = conn.prepareStatement(
                   "INSERT INTO purchaser_interest(purchaser_id, postcode) VALUES (?, ?) " +
                           "ON CONFLICT DO NOTHING")) {

        String today = LocalDate.now().toString();
        int created = 0;
        int interestCount = 0;
        int pendingInterests = 0;

        for (int i = 0; i < TOTAL_PURCHASERS; i++) {
          String first = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
          String last = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
          String email = (first + "." + last + i + "@example.com").toLowerCase();

          insPurchaser.setString(1, first + " " + last);
          insPurchaser.setString(2, email);
          insPurchaser.setString(3, today);

          long purchaserId;
          try (ResultSet rs = insPurchaser.executeQuery()) {
            if (!rs.next()) continue; // email collided, skip
            purchaserId = rs.getLong(1);
          }
          created++;

          // Pick 0..5 distinct postcodes
          int n = rnd.nextInt(MAX_POSTCODES + 1);
          Set<String> chosen = new HashSet<>();
          while (chosen.size() < n) {
            chosen.add(postcodes.get(rnd.nextInt(postcodes.size())));
          }
          for (String pc : chosen) {
            insInterest.setLong(1, purchaserId);
            insInterest.setString(2, pc);
            insInterest.addBatch();
            pendingInterests++;
            interestCount++;
          }

          if (pendingInterests >= BATCH_SIZE) {
            insInterest.executeBatch();
            pendingInterests = 0;
          }

          if ((i + 1) % 1000 == 0) {
            System.out.printf("Progress: %,d purchasers, %,d interests%n",
                    created, interestCount);
          }
        }

        if (pendingInterests > 0) insInterest.executeBatch();
        conn.commit();

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Done — %,d purchasers, %,d interests in %.2fs (avg %.2f interests/purchaser)%n",
                created, interestCount, elapsed / 1000.0,
                created == 0 ? 0 : interestCount / (double) created);
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    }
  }

  private static List<String> loadPostcodes() throws Exception {
    List<String> out = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery(
                 "SELECT DISTINCT post_code FROM property " +
                         "WHERE post_code IS NOT NULL AND post_code <> ''")) {
      while (rs.next()) out.add(rs.getString(1));
    }
    return out;
  }
}