package messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.nio.charset.StandardCharsets;

public class EventPublisher {

    public static final String QUEUE_NAME = "property.events";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Connection connection;
    private final Channel channel;

    public EventPublisher(String host, int port) {
        Connection conn = null;
        Channel ch = null;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            conn = factory.newConnection();
            ch = conn.createChannel();
            ch.queueDeclare(QUEUE_NAME, true, false, false, null);
            System.out.println("EventPublisher connected to RabbitMQ at " + host + ":" + port);
        } catch (Exception e) {
            // No broker available: degrade to no-op so HTTP endpoints still work.
            System.err.println("EventPublisher unavailable (" + e.getMessage() + ") — events will be dropped.");
            conn = null;
            ch = null;
        }
        this.connection = conn;
        this.channel = ch;
    }

    public void publish(PropertyEvent event) {
        if (channel == null) return;
        try {
            byte[] body = mapper.writeValueAsBytes(event);
            channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, body);
        } catch (Exception e) {
            System.err.println("EventPublisher.publish failed: " + e.getMessage());
        }
    }

    public void close() {
        try { if (channel != null) channel.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
    }
}
