package purchaser;

import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class PurchaserController {

    private final PurchaserDAO purchasers;

    public PurchaserController(PurchaserDAO purchasers) { this.purchasers = purchasers; }

    // GET /purchasers
    public void getAllPurchasers(Context ctx) {
        List<Purchaser> all = purchasers.getAllPurchasers();
        ctx.json(all);
    }

    // POST /purchaser  body: { "email": "...", "name": "..." }
    public void createPurchaser(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        Long id = purchasers.createPurchaser(body.get("email"), body.get("name"));
        if (id != null) ctx.status(201).json(Map.of("purchaserId", id));
        else ctx.status(400).result("Could not create (email may already exist)");
    }

    // GET /purchaser/{id}
    public Purchaser getPurchaser(Context ctx, String idStr) {
        Purchaser p = purchasers.getPurchaser(Long.parseLong(idStr));
        if (p == null) {
            ctx.status(404).result("Purchaser not found");
            return null;
        }
        ctx.json(p);
        return p;
    }

    // POST /purchaser/{id}/interest   body: { "postcode": "2000" }
    public void addInterest(Context ctx, String idStr) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        boolean ok = purchasers.addInterest(Long.parseLong(idStr), body.get("postcode"));
        if (ok) ctx.status(201).result("Interest added");
        else ctx.status(400).result("Could not add interest (max 5, duplicate, or unknown purchaser)");
    }

    // DELETE /purchaser/{id}/interest/{postcode}
    public void removeInterest(Context ctx, String idStr, String postcode) {
        if (purchasers.removeInterest(Long.parseLong(idStr), postcode)) {
            ctx.status(200).result("Interest removed");
        } else {
            ctx.status(404).result("Interest not found");
        }
    }
}