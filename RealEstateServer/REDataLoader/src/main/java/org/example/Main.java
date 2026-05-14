package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {

    private static final String PATH_TO_CSV =
        "/Users/kinseybellerose/Desktop/SydneyExercises/RealEstateServer/REDataLoader/src/main/java/org/example/nsw_property_data.csv";

    private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
    private static final String DB_USER = "realestate";
    private static final String DB_PASS = "realestate";

    public static void main(String[] args) {
        final Path csvFilePath = Paths.get(PATH_TO_CSV);
        long startTime = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            int inserted = CsvLoader.load(conn, csvFilePath);
            long elapsedMs = System.currentTimeMillis() - startTime;
            System.out.printf("Loaded %,d records in %.2f seconds%n",
                              inserted, elapsedMs / 1000.0);
        } catch (SQLException | IOException e) {
            System.err.println("Load failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
