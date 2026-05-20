package purchaser;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PurchaserDAO {

    private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
    private static final String DB_USER = "realestate";
    private static final String DB_PASS = "realestate";

    public static final int MAX_INTERESTS = 5;

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public Long createPurchaser(String email, String name) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO purchaser (email, name) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return null;
        } catch (SQLException e) {
            // UNIQUE constraint on email
            return null;
        }
    }

    public Purchaser getPurchaser(long purchaserId) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT purchaser_id, email, name FROM purchaser WHERE purchaser_id = ?")) {
            ps.setLong(1, purchaserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Purchaser p = new Purchaser();
                p.purchaserId = rs.getLong("purchaser_id");
                p.email = rs.getString("email");
                p.name = rs.getString("name");
                p.interests = interestsFor(conn, purchaserId);
                return p;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Single query with array_agg = O(1) DB round-trips, not O(N) like getPurchaser-in-a-loop would be.
    public List<Purchaser> getAllPurchasers() {
        String sql =
            "SELECT p.purchaser_id, p.email, p.name, " +
            "       COALESCE(array_agg(pi.postcode ORDER BY pi.postcode) " +
            "                FILTER (WHERE pi.postcode IS NOT NULL), '{}') AS postcodes " +
            "FROM purchaser p " +
            "LEFT JOIN purchaser_interest pi ON pi.purchaser_id = p.purchaser_id " +
            "GROUP BY p.purchaser_id, p.email, p.name " +
            "ORDER BY p.purchaser_id";
        List<Purchaser> out = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Purchaser p = new Purchaser();
                p.purchaserId = rs.getLong("purchaser_id");
                p.email = rs.getString("email");
                p.name = rs.getString("name");
                Array arr = rs.getArray("postcodes");
                p.interests = Arrays.asList((String[]) arr.getArray());
                out.add(p);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    /**
     * Add a postcode interest. Returns:
     *   - true  if added
     *   - false if purchaser doesn't exist, already at 5 interests, or postcode already registered
     * The 5-interest cap is enforced here (not in SQL).
     */
    public boolean addInterest(long purchaserId, String postcode) {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                int current;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM purchaser_interest WHERE purchaser_id = ?")) {
                    ps.setLong(1, purchaserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("COUNT query returned no row");
                        current = rs.getInt(1);
                    }
                }
                if (current >= MAX_INTERESTS) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO purchaser_interest (purchaser_id, postcode) VALUES (?, ?) " +
                        "ON CONFLICT (purchaser_id, postcode) DO NOTHING")) {
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
        try (Connection conn = connect();
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
