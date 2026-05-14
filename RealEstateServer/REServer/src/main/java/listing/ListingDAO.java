package listing;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ListingDAO {

    private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
    private static final String DB_USER = "realestate";
    private static final String DB_PASS = "realestate";

    private static final int MAX_RESULTS = 1000;

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /** Create a new listing for an existing property at an initial price. */
    public Optional<Long> createListing(long propertyId, String listedDate, long initialPrice) {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                // Verify property exists
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT 1 FROM property WHERE property_id = ?")) {
                    check.setLong(1, propertyId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) return Optional.empty();
                    }
                }
                long listingId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO listing (property_id, listed_date, status) VALUES (?, ?, 'active')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, propertyId);
                    ps.setString(2, listedDate);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        listingId = keys.getLong(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO listing_price (listing_id, price, effective_date) VALUES (?, ?, ?)")) {
                    ps.setLong(1, listingId);
                    ps.setLong(2, initialPrice);
                    ps.setString(3, listedDate);
                    ps.executeUpdate();
                }
                conn.commit();
                return Optional.of(listingId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("createListing failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Add a new price to an existing listing's history. */
    public boolean addPrice(long listingId, long newPrice, String effectiveDate) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO listing_price (listing_id, price, effective_date) " +
                             "SELECT ?, ?, ? WHERE EXISTS (SELECT 1 FROM listing WHERE listing_id = ?)")) {
            ps.setLong(1, listingId);
            ps.setLong(2, newPrice);
            ps.setString(3, effectiveDate);
            ps.setLong(4, listingId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Get listing details including full price history. */
    public Optional<Listing> getListing(long listingId) {
        String sql = """
            SELECT l.listing_id, l.property_id, l.listed_date, l.status,
                   (SELECT price FROM listing_price
                     WHERE listing_id = l.listing_id
                     ORDER BY effective_date DESC, listing_price_id DESC LIMIT 1) AS current_price
            FROM listing l WHERE l.listing_id = ?
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Listing l = mapRow(rs);
                l.priceHistory = priceHistoryFor(conn, listingId);
                return Optional.of(l);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Listing> getAllListings() {
        String sql = """
            SELECT l.listing_id, l.property_id, l.listed_date, l.status,
                   (SELECT price FROM listing_price
                     WHERE listing_id = l.listing_id
                     ORDER BY effective_date DESC, listing_price_id DESC LIMIT 1) AS current_price
            FROM listing l
            ORDER BY l.listed_date DESC
            LIMIT ?
            """;
        List<Listing> out = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, MAX_RESULTS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<Listing> getListingsForProperty(long propertyId) {
        String sql = """
            SELECT l.listing_id, l.property_id, l.listed_date, l.status,
                   (SELECT price FROM listing_price
                     WHERE listing_id = l.listing_id
                     ORDER BY effective_date DESC, listing_price_id DESC LIMIT 1) AS current_price
            FROM listing l WHERE l.property_id = ?
            ORDER BY l.listed_date DESC
            """;
        List<Listing> out = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, propertyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public boolean withdrawListing(long listingId) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE listing SET status = 'withdrawn' WHERE listing_id = ?")) {
            ps.setLong(1, listingId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private List<Listing.PriceEntry> priceHistoryFor(Connection conn, long listingId) throws SQLException {
        List<Listing.PriceEntry> hist = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT price, effective_date FROM listing_price " +
                        "WHERE listing_id = ? ORDER BY effective_date ASC, listing_price_id ASC")) {
            ps.setLong(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hist.add(new Listing.PriceEntry(rs.getLong("price"), rs.getString("effective_date")));
                }
            }
        }
        return hist;
    }

    private Listing mapRow(ResultSet rs) throws SQLException {
        Listing l = new Listing();
        l.listingId = rs.getLong("listing_id");
        l.propertyId = rs.getLong("property_id");
        l.listedDate = rs.getString("listed_date");
        l.status = rs.getString("status");
        l.currentPrice = rs.getLong("current_price");
        return l;
    }
}
