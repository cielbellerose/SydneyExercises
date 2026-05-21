package analytics;

import io.javalin.http.Context;

import java.util.Map;

public class PostcodeAnalyticsController {

    private final PostcodeAnalyticsDAO dao;

    public PostcodeAnalyticsController(PostcodeAnalyticsDAO dao) {
        this.dao = dao;
    }

    // GET /analytics/postcode/{postcode}
    public void postcodeHits(Context ctx, String postcode) {
        ctx.json(Map.of(
                "postcode", postcode,
                "interestedPurchasers", dao.interestedPurchasers(postcode)));
    }

    // GET /analytics/postcode/{postcode}/purchasers
    public void purchasersForPostcode(Context ctx, String postcode) {
        ctx.json(Map.of(
                "postcode", postcode,
                "purchasers", dao.purchasersForPostcode(postcode)));
    }
}
