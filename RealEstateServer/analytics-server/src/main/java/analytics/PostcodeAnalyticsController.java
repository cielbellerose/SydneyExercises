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
}
