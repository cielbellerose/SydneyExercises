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

public class PostcodeAnalyticsDAO {

    private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
    private static final String DB_USER = "realestate";
    private static final String DB_PASS = "realestate";

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public long interestedPurchasers(String postcode) {
        final String sql = "SELECT COUNT(*) FROM purchaser_interest WHERE postcode = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, postcode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            System.err.println("interestedPurchasers failed: " + e.getMessage());
            return 0L;
        }
    }

    public List<Map<String, Object>> purchasersForPostcode(String postcode) {
        final String sql =
            "SELECT p.purchaser_id, p.email, p.name " +
            "FROM purchaser p JOIN purchaser_interest i ON p.purchaser_id = i.purchaser_id " +
            "WHERE i.postcode = ?";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, postcode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("purchaserId", rs.getLong("purchaser_id"));
                    row.put("email", rs.getString("email"));
                    row.put("name", rs.getString("name"));
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("purchasersForPostcode failed: " + e.getMessage());
        }
        return out;
    }
}
