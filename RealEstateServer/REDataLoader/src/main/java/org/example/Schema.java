package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Schema {

    private Schema() {}

    public static void create(Connection conn) throws SQLException, IOException {
        executeScript(conn, "/db/schema.sql");
    }

    public static void createIndexes(Connection conn) throws SQLException, IOException {
        executeScript(conn, "/db/indexes.sql");
    }

    public static void dropIndexes(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("DROP INDEX IF EXISTS idx_property_postcode");
            s.execute("DROP INDEX IF EXISTS idx_property_price");
        }
    }

    private static void executeScript(Connection conn, String resourcePath) throws SQLException, IOException {
        String script = readResource(resourcePath);
        try (Statement s = conn.createStatement()) {
            for (String stmt : script.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    s.execute(trimmed);
                }
            }
        }
    }

    private static String readResource(String resourcePath) throws IOException {
        try (InputStream in = Schema.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
