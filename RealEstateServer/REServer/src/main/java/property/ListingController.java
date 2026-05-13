package property;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ListingController {

    private final ListingDAO listings;

    public ListingController(ListingDAO listings) { this.listings = listings; }

    // POST /listing  body: { "propertyId": 123, "listedDate": "2025-01-15", "initialPrice": 850000 }
    public void createListing(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        long propertyId = ((Number) body.get("propertyId")).longValue();
        String listedDate = (String) body.get("listedDate");
        long initialPrice = ((Number) body.get("initialPrice")).longValue();

        Optional<Long> id = listings.createListing(propertyId, listedDate, initialPrice);
        if (id.isPresent()) {
            ctx.status(201).json(Map.of("listingId", id.get()));
        } else {
            ctx.status(400).result("Could not create listing (property may not exist)");
        }
    }

    // PUT /listing/{id}/price   body: { "price": 800000, "effectiveDate": "2025-02-15" }
    public void changePrice(Context ctx, String listingIdStr) {
        long listingId = Long.parseLong(listingIdStr);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        long price = ((Number) body.get("price")).longValue();
        String effectiveDate = (String) body.get("effectiveDate");

        if (listings.addPrice(listingId, price, effectiveDate)) {
            ctx.status(200).result("Price added");
        } else {
            ctx.status(404).result("Listing not found");
        }
    }

    // GET /listing/{id}
    public void getListing(Context ctx, String listingIdStr) {
        Optional<Listing> l = listings.getListing(Long.parseLong(listingIdStr));
        if (l.isPresent()) ctx.json(l.get());
        else ctx.status(404).result("Listing not found");
    }

    // GET /property/{propertyId}/listings
    public void listingsForProperty(Context ctx, String propertyIdStr) {
        List<Listing> ls = listings.getListingsForProperty(Long.parseLong(propertyIdStr));
        ctx.json(ls);
    }

    // DELETE /listing/{id}  — withdraw
    public void withdraw(Context ctx, String listingIdStr) {
        if (listings.withdrawListing(Long.parseLong(listingIdStr))) {
            ctx.status(200).result("Listing withdrawn");
        } else {
            ctx.status(404).result("Listing not found");
        }
    }
}
