package app;

import analytics.PostcodeAnalyticsController;
import analytics.PostcodeAnalyticsDAO;
import io.javalin.Javalin;
import notifier.NotifyController;

import java.net.http.HttpClient;
import java.time.Duration;

public class AnalyticsServerMain {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("ANALYTICS_PORT", "7073"));
        String purchasersUrl = System.getenv().getOrDefault("PURCHASERS_SERVICE_URL", "http://localhost:7072");
        String propertyUrl = System.getenv().getOrDefault("PROPERTY_SERVICE_URL", "http://localhost:7071");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        PostcodeAnalyticsDAO dao = new PostcodeAnalyticsDAO();
        PostcodeAnalyticsController postcode = new PostcodeAnalyticsController(dao);
        NotifyController notify = new NotifyController(http, purchasersUrl, propertyUrl);

        Javalin app = Javalin.create()
                .get("/", ctx -> ctx.result("Analytics server is running"))
                .start(port);

        app.get("/notify/{purchaserId}", ctx -> notify.notifyPurchaser(ctx, ctx.pathParam("purchaserId")));
        app.get("/analytics/postcode/{postcode}", ctx -> postcode.postcodeHits(ctx, ctx.pathParam("postcode")));
    }
}
