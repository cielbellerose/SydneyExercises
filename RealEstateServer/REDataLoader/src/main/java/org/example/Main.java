package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {

    private static final String PATH_TO_CSV =
        "/Users/kinseybellerose/Desktop/SydneyExercises/RealEstateServer/REDataLoader/src/main/java/org/example/nsw_property_data.csv";

    // SQLite creates this file automatically on first connection — no separate "create database" step.
    private static final String DB_URL =
        "jdbc:sqlite:/Users/kinseybellerose/Desktop/SydneyExercises/RealEstateServer/realestate.db";

    public static void main(String[] args) {
        final Path csvFilePath = Paths.get(PATH_TO_CSV);
        long startTime = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            applyBulkLoadPragmas(conn);
            Schema.create(conn);

            // Drop indexes before inserting
            Schema.dropIndexes(conn);
            int inserted = PropertyLoader.load(conn, csvFilePath);
            Schema.createIndexes(conn);

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
}
