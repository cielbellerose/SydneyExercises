package gateway;

import io.javalin.Javalin;

import java.net.http.HttpClient;
import java.time.Duration;

public class Gateway {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("GATEWAY_PORT", "7070"));
        String propertyUrl   = System.getenv().getOrDefault("PROPERTY_SERVICE_URL",   "http://localhost:7071");
        String purchasersUrl = System.getenv().getOrDefault("PURCHASERS_SERVICE_URL", "http://localhost:7072");
        String analyticsUrl  = System.getenv().getOrDefault("ANALYTICS_SERVICE_URL",  "http://localhost:7073");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        Proxy proxy = new Proxy(http);
        AnalyticsClient analytics = new AnalyticsClient(http, analyticsUrl);

        Javalin app = Javalin.create()
                .get("/", ctx -> ctx.result("API Gateway is running"))
                .start(port);

        // =============== PROPERTIES → property-server ====================
        app.get("/property/{propertyID}", ctx -> {
            String id = ctx.pathParam("propertyID");
            proxy.forward(ctx, propertyUrl, "/property/" + id);
            if (ctx.status().getCode() < 400) analytics.recordProperty(id);
        });
        app.get("/properties", ctx -> proxy.forward(ctx, propertyUrl, "/properties"));
        app.post("/property", ctx -> proxy.forward(ctx, propertyUrl, "/property"));
        app.get("/property/postcode/{postcode}", ctx -> {
            String pc = ctx.pathParam("postcode");
            proxy.forward(ctx, propertyUrl, "/property/postcode/" + pc);
            if (ctx.status().getCode() < 400) analytics.recordPostcode(pc);
        });

        // =============== LISTINGS → property-server ====================
        app.post("/listing", ctx -> proxy.forward(ctx, propertyUrl, "/listing"));
        app.get("/listings", ctx -> proxy.forward(ctx, propertyUrl, "/listings"));
        app.get("/listing/{listingId}", ctx -> proxy.forward(ctx, propertyUrl, "/listing/" + ctx.pathParam("listingId")));
        app.post("/listing/{listingId}/price", ctx -> proxy.forward(ctx, propertyUrl, "/listing/" + ctx.pathParam("listingId") + "/price"));
        app.delete("/listing/{listingId}", ctx -> proxy.forward(ctx, propertyUrl, "/listing/" + ctx.pathParam("listingId")));
        app.get("/property/{propertyId}/listings", ctx -> proxy.forward(ctx, propertyUrl, "/property/" + ctx.pathParam("propertyId") + "/listings"));

        // =============== PURCHASERS → purchasers-server ====================
        app.get("/purchasers", ctx -> proxy.forward(ctx, purchasersUrl, "/purchasers"));
        app.post("/purchaser", ctx -> proxy.forward(ctx, purchasersUrl, "/purchaser"));
        app.get("/purchaser/{purchaserId}", ctx -> proxy.forward(ctx, purchasersUrl, "/purchaser/" + ctx.pathParam("purchaserId")));
        app.post("/purchaser/{id}/interest", ctx -> proxy.forward(ctx, purchasersUrl, "/purchaser/" + ctx.pathParam("id") + "/interest"));
        app.delete("/purchaser/{id}/interest/{postcode}", ctx ->
                proxy.forward(ctx, purchasersUrl, "/purchaser/" + ctx.pathParam("id") + "/interest/" + ctx.pathParam("postcode")));

        // =============== NOTIFY + ANALYTICS → analytics-server ====================
        app.get("/notify/{purchaserId}", ctx -> proxy.forward(ctx, analyticsUrl, "/notify/" + ctx.pathParam("purchaserId")));
        app.get("/analytics/property/{id}", ctx -> proxy.forward(ctx, analyticsUrl, "/analytics/property/" + ctx.pathParam("id")));
        app.get("/analytics/postcode/{postcode}", ctx -> proxy.forward(ctx, analyticsUrl, "/analytics/postcode/" + ctx.pathParam("postcode")));
        app.get("/analytics/top", ctx -> proxy.forward(ctx, analyticsUrl, "/analytics/top"));
    }
}
