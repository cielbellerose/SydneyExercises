package analytics;

import io.javalin.http.Context;

import java.util.Map;
import java.util.Set;

public class AccessCountController {

    private static final Set<String> ALLOWED_TYPES = Set.of("property", "postcode");

    private final AccessCountDAO dao;

    public AccessCountController(AccessCountDAO dao) {
        this.dao = dao;
    }

    // POST /analytics/access   body: { "type": "property"|"postcode", "id": "..." }
    public void record(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String type = body.get("type");
        String id = body.get("id");
        if (type == null || id == null || !ALLOWED_TYPES.contains(type)) {
            ctx.status(400).json(Map.of("error", "type must be 'property' or 'postcode' and id required"));
            return;
        }
        if (dao.record(type, id)) {
            ctx.status(202).json(Map.of("recorded", true));
        } else {
            ctx.status(500).json(Map.of("error", "Failed to record access"));
        }
    }

    // GET /analytics/property/{id}
    public void propertyHits(Context ctx, String id) {
        ctx.json(Map.of("type", "property", "id", id, "hits", dao.getHits("property", id)));
    }

    // GET /analytics/postcode/{postcode}
    public void postcodeHits(Context ctx, String postcode) {
        ctx.json(Map.of("type", "postcode", "id", postcode, "hits", dao.getHits("postcode", postcode)));
    }

    // GET /analytics/top?type=property|postcode&limit=N
    public void top(Context ctx) {
        String type = ctx.queryParamAsClass("type", String.class).getOrDefault("property");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        if (!ALLOWED_TYPES.contains(type)) {
            ctx.status(400).json(Map.of("error", "type must be 'property' or 'postcode'"));
            return;
        }
        ctx.json(Map.of("type", type, "results", dao.top(type, limit)));
    }
}
