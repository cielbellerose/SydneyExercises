package app;

import io.javalin.Javalin;
import listing.ListingController;
import listing.ListingDAO;
import property.PropertyController;
import property.PropertyDAO;

public class PropertyServerMain {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PROPERTY_PORT", "7071"));

        PropertyDAO properties = new PropertyDAO();
        ListingDAO listings = new ListingDAO();
        PropertyController propertyHandler = new PropertyController(properties);
        ListingController listingHandler = new ListingController(listings);

        Javalin app = Javalin.create()
                .get("/", ctx -> ctx.result("Property server is running"))
                .start(port);

        // =============== PROPERTIES ====================
        app.get("/property/{propertyID}", ctx -> propertyHandler.getPropertyByID(ctx, ctx.pathParam("propertyID")));
        app.get("/property/{propertyID}/views", ctx -> propertyHandler.getPropertyViews(ctx, ctx.pathParam("propertyID")));
        app.get("/properties", propertyHandler::getAllProperties);
        app.post("/property", propertyHandler::createProperty);
        app.get("/property/postcode/{postcode}", ctx -> propertyHandler.findPropertyByPostCode(ctx, ctx.pathParam("postcode")));

        // =============== LISTINGS ====================
        app.post("/listing", listingHandler::createListing);
        app.get("/listings", listingHandler::getAllListings);
        app.get("/listing/{listingId}", ctx -> listingHandler.getListing(ctx, ctx.pathParam("listingId")));
        app.post("/listing/{listingId}/price", ctx -> listingHandler.changePrice(ctx, ctx.pathParam("listingId")));
        app.delete("/listing/{listingId}", ctx -> listingHandler.withdraw(ctx, ctx.pathParam("listingId")));
        app.get("/property/{propertyId}/listings", ctx -> listingHandler.listingsForProperty(ctx, ctx.pathParam("propertyId")));
    }
}
