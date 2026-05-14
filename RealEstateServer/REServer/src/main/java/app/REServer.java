package app;
import io.javalin.Javalin;
import listing.ListingDAO;
import notifier.NotifyController;
import notifier.NotifyDAO;
import property.PropertyDAO;
import purchaser.PurchaserDAO;
import listing.ListingController;
import property.PropertyController;
import purchaser.PurchaserController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class REServer {
        private static final Logger LOG = LoggerFactory.getLogger(REServer.class);

        public static void main(String[] args) {

            // in memory test data store
            var properties = new PropertyDAO();
            var purchasers = new PurchaserDAO();
            var listings = new ListingDAO();
            var notifications = new NotifyDAO();

            // API implementation
            PropertyController propertyHandler = new PropertyController(properties);
            PurchaserController purchaserHandler = new PurchaserController(purchasers);
            ListingController listingHandler = new ListingController(listings);
            NotifyController notifyHandler = new NotifyController(purchasers, properties);

            // start Javalin on port 7070
            var app = Javalin.create()
                    .get("/", ctx -> ctx.result("Real Estate server is running"))
                    .start(7070);

            // Property records are immutable hence no PUT and DELETE

            // =============== PROPERTIES ====================
            // return a property by property ID
            app.get("/property/{propertyID}", ctx -> {
                propertyHandler.getPropertyByID(ctx, ctx.pathParam("propertyID"));
            });
            // get all property records - could be big!
            app.get("/properties", ctx -> {
                propertyHandler.getAllProperties(ctx);
            });
            // create a new property record
            app.post("/property", ctx -> {
                propertyHandler.createProperty(ctx);
            });
            // Get all properties for a specified postcode
            app.get("/property/postcode/{postcode}", ctx -> {
                propertyHandler.findPropertyByPostCode(ctx, ctx.pathParam("postcode"));
            });


            // =============== PURCHASERS ====================
            // Get all purchasers (with their postcode interests)
            app.get("/purchasers", ctx -> {
                purchaserHandler.getAllPurchasers(ctx);
            });
            // create purchaser
            app.post("/purchaser", ctx -> {
                purchaserHandler.createPurchaser(ctx);
            });
            // get purchaser by id
            app.get("/purchaser/{purchaserId}", ctx -> {
                purchaserHandler.getPurchaser(ctx, ctx.pathParam("purchaserId"));
            });
            // add interest
            app.post("/purchaser/{id}/interest", ctx -> {
                purchaserHandler.addInterest(ctx, ctx.pathParam("id"));
            });
            // remove interest
            app.delete("/purchaser/{id}/interest/{postcode}", ctx -> {
                purchaserHandler.removeInterest(ctx, ctx.pathParam("id"),ctx.pathParam("postcode"));
            });


            // =============== LISTINGS ====================

            // Creating listings
            app.post("/listing", ctx -> {
                listingHandler.createListing(ctx);
            });

            // get all listings - capped at MAX_RESULTS
            app.get("/listings", ctx -> {
                listingHandler.getAllListings(ctx);
            });

            // Getting Listings
            app.get("/listing/{listingId}", ctx -> {
                listingHandler.getListing(ctx, ctx.pathParam("listingId"));
            });

            // Changing price
            app.post("/listing/{listingId}/price", ctx -> {
                listingHandler.changePrice(ctx, ctx.pathParam("listingId"));
            });

            // Deleting listing
            app.delete("/listing/{listingId}", ctx -> {
                listingHandler.withdraw(ctx, ctx.pathParam("listingId"));
            });

            // Listings for specific property
            app.get("/property/{propertyId}/listings", ctx -> {
                listingHandler.listingsForProperty(ctx, ctx.pathParam("propertyId"));
            });


            // =============== NOTIFIER ====================
            app.get("/notify/{purchaserId}", ctx -> {
                notifyHandler.notifyPurchaser(ctx, ctx.pathParam("purchaserId"));
            });
        }
}
