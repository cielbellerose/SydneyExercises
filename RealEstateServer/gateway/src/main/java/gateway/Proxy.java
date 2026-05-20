package gateway;

import io.javalin.http.Context;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Forwards a single Javalin request to one of the backend services and copies
 * the backend response back onto the client's response.
 */
public class Proxy {

    private final HttpClient http;

    public Proxy(HttpClient http) {
        this.http = http;
    }

    public void forward(Context ctx, String backendBaseUrl, String backendPath) {
        URI uri = URI.create(backendBaseUrl + backendPath + queryString(ctx));

        HttpRequest.Builder b = HttpRequest.newBuilder(uri);
        String method = ctx.method().name();
        switch (method) {
            case "GET":
                b.GET();
                break;
            case "DELETE":
                b.DELETE();
                break;
            case "POST":
                b.POST(HttpRequest.BodyPublishers.ofByteArray(ctx.bodyAsBytes()));
                break;
            case "PUT":
                b.PUT(HttpRequest.BodyPublishers.ofByteArray(ctx.bodyAsBytes()));
                break;
            default:
                ctx.status(405).result("Method not allowed");
                return;
        }
        String contentType = ctx.contentType();
        if (contentType != null) b.header("Content-Type", contentType);

        try {
            HttpResponse<byte[]> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            ctx.status(resp.statusCode());
            resp.headers().firstValue("content-type").ifPresent(ct -> ctx.contentType(ct));
            ctx.result(resp.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            ctx.status(502).result("Upstream service unreachable: " + e.getMessage());
        }
    }

    private static String queryString(Context ctx) {
        Map<String, java.util.List<String>> params = ctx.queryParamMap();
        if (params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, java.util.List<String>> e : params.entrySet()) {
            for (String v : e.getValue()) {
                if (!first) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                  .append('=')
                  .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
                first = false;
            }
        }
        return sb.toString();
    }
}
