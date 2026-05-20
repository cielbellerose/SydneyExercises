package analytics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AccessCountDAO {

    private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
    private static final String DB_USER = "realestate";
    private static final String DB_PASS = "realestate";

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public boolean record(String type, String id) {
        final String sql =
                "INSERT INTO access_counts (target_type, target_id, hits) VALUES (?, ?, 1) " +
                "ON CONFLICT (target_type, target_id) DO UPDATE SET hits = access_counts.hits + 1";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, id);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("record access failed: " + e.getMessage());
            return false;
        }
    }

    public long getHits(String type, String id) {
        final String sql = "SELECT hits FROM access_counts WHERE target_type = ? AND target_id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            System.err.println("getHits failed: " + e.getMessage());
            return 0L;
        }
    }

    public List<Map<String, Object>> top(String type, int limit) {
        final String sql =
                "SELECT target_id, hits FROM access_counts " +
                "WHERE target_type = ? ORDER BY hits DESC LIMIT ?";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("target_id"));
                    row.put("hits", rs.getLong("hits"));
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("top failed: " + e.getMessage());
        }
        return out;
    }
}
