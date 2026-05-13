package property;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PurchaserDAO {

    private static final String DB_URL =
            "jdbc:sqlite:C:/Users/watso/Downloads/CS4530/Assignment1/realestate.db";

    public static final int MAX_INTERESTS = 5;

    public Optional<Long> createPurchaser(String email, String name) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO purchaser (email, name) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return Optional.of(keys.getLong(1));
            }
            return Optional.empty();
        } catch (SQLException e) {
            // UNIQUE constraint on email
            return Optional.empty();
        }
    }

    public Optional<Purchaser> getPurchaser(long purchaserId) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT purchaser_id, email, name FROM purchaser WHERE purchaser_id = ?")) {
            ps.setLong(1, purchaserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Purchaser p = new Purchaser();
                p.purchaserId = rs.getLong("purchaser_id");
                p.email = rs.getString("email");
                p.name = rs.getString("name");
                p.interests = interestsFor(conn, purchaserId);
                return Optional.of(p);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a postcode interest. Returns:
     *   - true  if added
     *   - false if purchaser doesn't exist, already at 5 interests, or postcode already registered
     * The 5-interest cap is enforced here (not in SQL).
     */
    public boolean addInterest(long purchaserId, String postcode) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                // Lock-step: count current interests, insert if under cap. Two statements
                // because SQLite doesn't have a clean way to do this in one.
                int current;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM purchaser_interest WHERE purchaser_id = ?")) {
                    ps.setLong(1, purchaserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        current = rs.getInt(1);
                    }
                }
                if (current >= MAX_INTERESTS) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO purchaser_interest (purchaser_id, postcode) VALUES (?, ?)")) {
                    ps.setLong(1, purchaserId);
                    ps.setString(2, postcode);
                    int updated = ps.executeUpdate();
                    conn.commit();
                    return updated > 0;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean removeInterest(long purchaserId, String postcode) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM purchaser_interest WHERE purchaser_id = ? AND postcode = ?")) {
            ps.setLong(1, purchaserId);
            ps.setString(2, postcode);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private List<String> interestsFor(Connection conn, long purchaserId) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT postcode FROM purchaser_interest WHERE purchaser_id = ? ORDER BY postcode")) {
            ps.setLong(1, purchaserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("postcode"));
            }
        }
        return out;
    }
}
