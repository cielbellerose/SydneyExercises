package notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.MessageProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class EventConsumer {

    private final Channel channel;
    private final HttpClient http;
    private final String analyticsBaseUrl;
    private final String eventsQueue;
    private final String messagesQueue;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventConsumer(Channel channel, HttpClient http, String analyticsBaseUrl,
                         String eventsQueue, String messagesQueue) {
        this.channel = channel;
        this.http = http;
        this.analyticsBaseUrl = analyticsBaseUrl;
        this.eventsQueue = eventsQueue;
        this.messagesQueue = messagesQueue;
    }

    public void start() throws Exception {
        DeliverCallback cb = (consumerTag, delivery) -> {
            try {
                JsonNode event = mapper.readTree(delivery.getBody());
                handle(event);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                System.err.println("EventConsumer failed: " + e.getMessage());
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            }
        };
        channel.basicConsume(eventsQueue, false, cb, tag -> {});
    }

    private void handle(JsonNode event) throws Exception {
        String postcode = event.path("postcode").asText();
        if (postcode.isEmpty()) return;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(analyticsBaseUrl + "/analytics/postcode/" + postcode + "/purchasers"))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.err.println("analytics lookup failed: HTTP " + resp.statusCode());
            return;
        }
        JsonNode body = mapper.readTree(resp.body());
        JsonNode purchasers = body.path("purchasers");
        if (!purchasers.isArray() || purchasers.isEmpty()) return;

        String subject = Formatter.subjectFor(event);
        String message = Formatter.bodyFor(event);
        String now = Instant.now().toString();

        for (JsonNode purchaser : purchasers) {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("purchaserId", purchaser.path("purchaserId").asLong());
            msg.put("purchaserEmail", purchaser.path("email").asText());
            msg.put("purchaserName", purchaser.path("name").asText());
            msg.put("subject", subject);
            msg.put("body", message);
            msg.put("eventType", event.path("eventType").asText());
            msg.put("propertyId", event.path("propertyId").asText());
            msg.put("postcode", postcode);
            msg.put("timestamp", now);
            channel.basicPublish("", messagesQueue, MessageProperties.PERSISTENT_TEXT_PLAIN,
                    mapper.writeValueAsBytes(msg));
        }
    }
}
