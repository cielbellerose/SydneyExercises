package messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: verifies property-server publishes the expected
 * PropertyEvent to the `property.events` RabbitMQ queue for each of the four
 * domain events (NEW_LISTING, STATUS_CHANGE, PRICE_CHANGE, HOT_PROPERTY).
 *
 * Prerequisites — start these before running the test:
 *   - Postgres on localhost:8000   (`make up`)
 *   - RabbitMQ on localhost:5672   (`make up`)
 *   - property-server on :7071     (`make run-property`)
 *
 * notification-server MUST be stopped while this test runs. Its EventConsumer
 * subscribes to the same `property.events` queue and would race the test for
 * messages, leading to flaky failures.
 */
class EventFlowTest {

    private static final String TEST_POSTCODE = "9999";
    private static final int TEST_PURCHASER_ID = 90001;
    private static final String QUEUE = "property.events";
    private static final String PROPERTY_BASE_URL = "http://localhost:7071";
    private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
    private static final String DB_USER = "realestate";
    private static final String DB_PASS = "realestate";
    private static final int POLL_TIMEOUT_SECONDS = 5;

    private static Connection rabbitConn;
    private static Channel channel;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient http = HttpClient.newHttpClient();
    private static final LinkedBlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();

    @BeforeAll
    static void setUp() throws Exception {
        resetTestData();
        try (java.sql.Connection db = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = db.createStatement()) {
            st.execute("INSERT INTO purchaser (purchaser_id, email, name) VALUES ("
                    + TEST_PURCHASER_ID + ", 'test-purchaser@test.local', 'Test Purchaser')");
            st.execute("INSERT INTO purchaser_interest (purchaser_id, postcode) VALUES ("
                    + TEST_PURCHASER_ID + ", '" + TEST_POSTCODE + "')");
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        rabbitConn = factory.newConnection();
        channel = rabbitConn.createChannel();
        channel.queueDeclare(QUEUE, true, false, false, null);
        channel.queuePurge(QUEUE);

        DeliverCallback cb = (consumerTag, delivery) -> {
            try {
                JsonNode event = mapper.readTree(delivery.getBody());
                if (TEST_POSTCODE.equals(event.path("postcode").asText())) {
                    received.offer(event);
                }
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            }
        };
        channel.basicConsume(QUEUE, false, cb, tag -> {});
    }

    @AfterAll
    static void tearDown() throws Exception {
        try { if (channel != null) channel.close(); } catch (Exception ignored) {}
        try { if (rabbitConn != null) rabbitConn.close(); } catch (Exception ignored) {}
        resetTestData();
    }

    @AfterEach
    void drainReceived() {
        received.clear();
    }

    @Test
    void newListingEmitsEvent() throws Exception {
        HttpResponse<String> resp = sendJson("POST", "/property",
                "{\"propertyID\":\"9999001\",\"postcode\":\"9999\",\"propertyPrice\":\"850000\",\"forSale\":true}");
        assertEquals(201, resp.statusCode(), resp.body());

        JsonNode event = received.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event, "Expected NEW_LISTING event within " + POLL_TIMEOUT_SECONDS + "s");
        assertEquals("NEW_LISTING", event.path("eventType").asText());
        assertEquals("9999001", event.path("propertyId").asText());
        assertEquals(TEST_POSTCODE, event.path("postcode").asText());
        assertEquals(850000L, event.path("price").asLong());
        assertTrue(event.path("forSale").asBoolean());
    }

    @Test
    void statusChangeEmitsEvent() throws Exception {
        createTestProperty("9999002", true);

        HttpResponse<String> resp = sendJson("POST", "/property/9999002/forSale",
                "{\"forSale\":false}");
        assertEquals(200, resp.statusCode(), resp.body());

        JsonNode event = received.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event, "Expected STATUS_CHANGE event within " + POLL_TIMEOUT_SECONDS + "s");
        assertEquals("STATUS_CHANGE", event.path("eventType").asText());
        assertEquals("9999002", event.path("propertyId").asText());
        assertEquals(TEST_POSTCODE, event.path("postcode").asText());
        assertFalse(event.path("forSale").asBoolean());
    }

    @Test
    void priceChangeEmitsEvent() throws Exception {
        createTestProperty("9999003", true);
        long listingId = createTestListing(9999003L, 500000L);

        HttpResponse<String> resp = sendJson("POST", "/listing/" + listingId + "/price",
                "{\"price\":790000,\"effectiveDate\":\"2026-06-01\"}");
        assertEquals(201, resp.statusCode(), resp.body());

        JsonNode event = received.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event, "Expected PRICE_CHANGE event within " + POLL_TIMEOUT_SECONDS + "s");
        assertEquals("PRICE_CHANGE", event.path("eventType").asText());
        assertEquals("9999003", event.path("propertyId").asText());
        assertEquals(TEST_POSTCODE, event.path("postcode").asText());
        assertEquals(790000L, event.path("price").asLong());
    }

    @Test
    void hotPropertyEmitsEvent() throws Exception {
        createTestProperty("9999004", true);

        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(PROPERTY_BASE_URL + "/property/9999004")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), resp.body());

        JsonNode event = received.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event, "Expected HOT_PROPERTY event within " + POLL_TIMEOUT_SECONDS + "s");
        assertEquals("HOT_PROPERTY", event.path("eventType").asText());
        assertEquals("9999004", event.path("propertyId").asText());
        assertEquals(TEST_POSTCODE, event.path("postcode").asText());
        assertTrue(event.path("viewCount").asLong() >= 1);
    }

    private void createTestProperty(String id, boolean forSale) throws Exception {
        HttpResponse<String> resp = sendJson("POST", "/property",
                String.format("{\"propertyID\":\"%s\",\"postcode\":\"%s\",\"propertyPrice\":\"500000\",\"forSale\":%s}",
                        id, TEST_POSTCODE, forSale));
        assertEquals(201, resp.statusCode(), resp.body());
        if (forSale) {
            // Drain the NEW_LISTING event the create produced so the next poll sees the test's target event.
            received.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private long createTestListing(long propertyId, long initialPrice) throws Exception {
        HttpResponse<String> resp = sendJson("POST", "/listing",
                String.format("{\"propertyId\":%d,\"listedDate\":\"2026-05-01\",\"initialPrice\":%d}",
                        propertyId, initialPrice));
        assertEquals(201, resp.statusCode(), resp.body());
        return mapper.readTree(resp.body()).path("listingId").asLong();
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(PROPERTY_BASE_URL + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void resetTestData() throws Exception {
        try (java.sql.Connection db = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = db.createStatement()) {
            st.execute("DELETE FROM listing_price WHERE listing_id IN (SELECT listing_id FROM listing WHERE property_id IN (9999001,9999002,9999003,9999004))");
            st.execute("DELETE FROM listing       WHERE property_id IN (9999001,9999002,9999003,9999004)");
            st.execute("DELETE FROM property      WHERE property_id IN (9999001,9999002,9999003,9999004)");
            st.execute("DELETE FROM purchaser_interest WHERE purchaser_id = " + TEST_PURCHASER_ID);
            st.execute("DELETE FROM purchaser           WHERE purchaser_id = " + TEST_PURCHASER_ID);
        }
    }
}
