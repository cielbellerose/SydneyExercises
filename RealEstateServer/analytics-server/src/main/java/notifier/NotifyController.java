package notifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Builds /notify responses by fanning out to the Purchasers Server and the
 * Property Server over HTTP. Replaces the old in-process DAO calls.
 */
public class NotifyController {

    private final HttpClient http;
    private final String purchasersBaseUrl;
    private final String propertyBaseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public NotifyController(HttpClient http, String purchasersBaseUrl, String propertyBaseUrl) {
        this.http = http;
        this.purchasersBaseUrl = purchasersBaseUrl;
        this.propertyBaseUrl = propertyBaseUrl;
    }

    // GET /notify/{purchaserId}
    public Notification notifyPurchaser(Context ctx, String purchaserIdStr) {
        long purchaserId;
        try {
            purchaserId = Long.parseLong(purchaserIdStr);
        } catch (NumberFormatException e) {
            ctx.status(400).result("Invalid purchaser id");
            return null;
        }

        JsonNode purchaser;
        try {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(purchasersBaseUrl + "/purchaser/" + purchaserId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                ctx.status(404).result("Purchaser not found");
                return null;
            }
            if (resp.statusCode() != 200) {
                ctx.status(502).result("Purchasers service error");
                return null;
            }
            purchaser = json.readTree(resp.body());
        } catch (IOException | InterruptedException e) {
            ctx.status(502).result("Purchasers service unreachable");
            return null;
        }

        List<String> interests = new ArrayList<>();
        JsonNode interestsNode = purchaser.get("interests");
        if (interestsNode != null && interestsNode.isArray()) {
            interestsNode.forEach(n -> interests.add(n.asText()));
        }
        if (interests.isEmpty()) {
            ctx.status(404).result("No interests found");
            return null;
        }

        String email = purchaser.path("email").asText(null);
        String pid = purchaser.path("purchaserId").asText(purchaserIdStr);

        HashMap<String, List<Notification.PropertyMatch>> results = new HashMap<>();
        for (String postcode : interests) {
            List<Notification.PropertyMatch> matches = matchesFor(postcode);
            if (!matches.isEmpty()) {
                results.put(postcode, matches);
            }
        }

        Notification notification = new Notification(pid, email, interests);
        notification.setPostcodeResults(results);
        ctx.json(notification);
        return notification;
    }

    private List<Notification.PropertyMatch> matchesFor(String postcode) {
        List<Notification.PropertyMatch> matches = new ArrayList<>();
        HttpResponse<String> resp;
        try {
            resp = http.send(
                    HttpRequest.newBuilder(URI.create(propertyBaseUrl + "/property/postcode/" + postcode)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            return matches;
        }
        if (resp.statusCode() != 200) return matches;

        JsonNode arr;
        try {
            arr = json.readTree(resp.body());
        } catch (IOException e) {
            return matches;
        }
        if (!arr.isArray()) return matches;

        for (JsonNode node : arr) {
            String propertyId = node.path("propertyID").asText(null);
            String priceStr = node.path("propertyPrice").asText("");
            if (propertyId == null || priceStr.isEmpty()) continue;
            long price;
            try {
                price = Long.parseLong(priceStr);
            } catch (NumberFormatException e) {
                continue;
            }
            matches.add(new Notification.PropertyMatch(propertyId, price));
        }
        return matches;
    }
}
