package app;

import io.javalin.Javalin;
import purchaser.PurchaserController;
import purchaser.PurchaserDAO;

public class PurchasersServerMain {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PURCHASERS_PORT", "7072"));

        PurchaserDAO purchasers = new PurchaserDAO();
        PurchaserController purchaserHandler = new PurchaserController(purchasers);

        Javalin app = Javalin.create()
                .get("/", ctx -> ctx.result("Purchasers server is running"))
                .start(port);

        app.get("/purchasers", purchaserHandler::getAllPurchasers);
        app.post("/purchaser", purchaserHandler::createPurchaser);
        app.get("/purchaser/{purchaserId}", ctx -> purchaserHandler.getPurchaser(ctx, ctx.pathParam("purchaserId")));
        app.post("/purchaser/{id}/interest", ctx -> purchaserHandler.addInterest(ctx, ctx.pathParam("id")));
        app.delete("/purchaser/{id}/interest/{postcode}", ctx ->
                purchaserHandler.removeInterest(ctx, ctx.pathParam("id"), ctx.pathParam("postcode")));
    }
}
