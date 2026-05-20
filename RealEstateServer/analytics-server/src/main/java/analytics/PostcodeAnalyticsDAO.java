package analytics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
}
