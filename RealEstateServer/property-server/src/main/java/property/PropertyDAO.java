package property;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PropertyDAO {

    // Same Postgres instance the REDataLoader writes to (see docker-compose.yml).
    private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
    private static final String DB_USER = "realestate";
    private static final String DB_PASS = "realestate";

    // HACK: GET /property and GET /property/prices have no pagination, but the table has
    // ~4.85M rows. Returning all of them would OOM the JVM and the HTTP client. We cap the
    // result set so the API still responds. See README for the full discussion.
    private static final int MAX_RESULTS = 1000;

    private final Connection conn;

    public PropertyDAO() {
        try {
            this.conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open Postgres database at " + DB_URL, e);
        }
    }

    public boolean newProperty(Property property) {
        final String sql =
            "INSERT INTO property (property_id, post_code, purchase_price, for_sale) " +
            "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(property.propertyID));
            ps.setString(2, property.postcode);
            ps.setLong(3, Long.parseLong(property.propertyPrice));
            ps.setBoolean(4, property.forSale);
            return ps.executeUpdate() == 1;
        } catch (SQLException | NumberFormatException e) {
            System.err.println("newProperty failed: " + e.getMessage());
            return false;
        }
    }

    public Optional<Property> getPropertyById(String propertyID) {
        final String sql =
            "SELECT property_id, post_code, purchase_price, for_sale " +
            "FROM property WHERE property_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(propertyID));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("getPropertyById failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void incrementViewCount(String propertyID) {
        final String sql = "UPDATE property SET view_count = view_count + 1 WHERE property_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(propertyID));
            ps.executeUpdate();
        } catch (SQLException | NumberFormatException e) {
            System.err.println("incrementViewCount failed: " + e.getMessage());
        }
    }

    public Optional<Long> getViewCount(String propertyID) {
        final String sql = "SELECT view_count FROM property WHERE property_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(propertyID));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("getViewCount failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<Property> getPropertiesByPostCode(String postCode) {
        final String sql =
            "SELECT property_id, post_code, purchase_price, for_sale " +
            "FROM property WHERE post_code = ? LIMIT ?";
        List<Property> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, postCode);
            ps.setInt(2, MAX_RESULTS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("getPropertiesByPostCode failed: " + e.getMessage());
            return Collections.emptyList();
        }
        return out;
    }

    public List<String> getAllPropertyPrices() {
        // LIMIT applied here is the same hack as on getAllProperties — see MAX_RESULTS comment.
        final String sql = "SELECT purchase_price FROM property LIMIT ?";
        List<String> prices = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, MAX_RESULTS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long p = rs.getLong(1);
                    // wasNull() must be called *after* getLong() — JDBC quirk for primitives.
                    prices.add(rs.wasNull() ? "" : String.valueOf(p));
                }
            }
        } catch (SQLException e) {
            System.err.println("getAllPropertyPrices failed: " + e.getMessage());
            return Collections.emptyList();
        }
        return prices;
    }

    public List<Property> getAllProperties() {
        final String sql =
            "SELECT property_id, post_code, purchase_price, for_sale " +
            "FROM property LIMIT ?";
        List<Property> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, MAX_RESULTS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("getAllProperties failed: " + e.getMessage());
            return Collections.emptyList();
        }
        return out;
    }

    public List<Property> getPropertiesByPriceRange(long minPrice, long maxPrice) {
        final String sql =
            "SELECT property_id, post_code, purchase_price, for_sale " +
            "FROM property WHERE purchase_price BETWEEN ? AND ? LIMIT ?";
        List<Property> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, minPrice);
            ps.setLong(2, maxPrice);
            ps.setInt(3, MAX_RESULTS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("getPropertiesByPriceRange failed: " + e.getMessage());
            return Collections.emptyList();
        }
        return out;
    }

    // Turns one ResultSet row into a Property. The Property model only uses 4 of the table's
    // 18 columns — the rest are stored but not exposed via this API.
    private static Property mapRow(ResultSet rs) throws SQLException {
        long price = rs.getLong("purchase_price");
        String priceStr = rs.wasNull() ? "" : String.valueOf(price);
        Property p = new Property(
            String.valueOf(rs.getInt("property_id")),
            rs.getString("post_code"),
            priceStr
        );
        p.forSale = rs.getBoolean("for_sale");
        return p;
    }
}
