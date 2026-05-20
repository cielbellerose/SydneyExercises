package gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Fire-and-forget client that POSTs access events to the Analytics service.
 * Failures are logged but never propagated — they must not affect client latency
 * or success of the request that triggered the event.
 */
public class AnalyticsClient {

    private final HttpClient http;
    private final String analyticsBaseUrl;

    public AnalyticsClient(HttpClient http, String analyticsBaseUrl) {
        this.http = http;
        this.analyticsBaseUrl = analyticsBaseUrl;
    }

    public void recordProperty(String propertyId) {
        record("property", propertyId);
    }

    public void recordPostcode(String postcode) {
        record("postcode", postcode);
    }

    private void record(String type, String id) {
        if (id == null || id.isEmpty()) return;
        String body = "{\"type\":\"" + type + "\",\"id\":\"" + escape(id) + "\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(analyticsBaseUrl + "/analytics/access"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    System.err.println("analytics record failed for " + type + " " + id + ": " + ex.getMessage());
                    return null;
                });
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
