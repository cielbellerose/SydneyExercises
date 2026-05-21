package app;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import notification.EventConsumer;
import notification.PrinterConsumer;

import java.net.http.HttpClient;
import java.time.Duration;

public class NotificationServerMain {

    public static final String PROPERTY_EVENTS_QUEUE = "property.events";
    public static final String PURCHASER_MESSAGES_QUEUE = "purchaser.messages";

    public static void main(String[] args) throws Exception {
        String rabbitHost = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        int rabbitPort = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
        String analyticsUrl = System.getenv().getOrDefault("ANALYTICS_SERVICE_URL", "http://localhost:7073");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setPort(rabbitPort);

        Connection connection;
        try {
            connection = factory.newConnection();
        } catch (java.net.ConnectException e) {
            System.err.println("Cannot connect to RabbitMQ at " + rabbitHost + ":" + rabbitPort
                    + ". Is it running? Try: `make up` (then re-run `make run-notification`).");
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { connection.close(); } catch (Exception ignored) {}
        }));

        Channel eventChannel = connection.createChannel();
        Channel printChannel = connection.createChannel();
        eventChannel.queueDeclare(PROPERTY_EVENTS_QUEUE, true, false, false, null);
        eventChannel.queueDeclare(PURCHASER_MESSAGES_QUEUE, true, false, false, null);

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        new EventConsumer(eventChannel, http, analyticsUrl,
                PROPERTY_EVENTS_QUEUE, PURCHASER_MESSAGES_QUEUE).start();
        new PrinterConsumer(printChannel, PURCHASER_MESSAGES_QUEUE).start();

        System.out.println("notification-server running. Connected to RabbitMQ at "
                + rabbitHost + ":" + rabbitPort + ", analytics=" + analyticsUrl);

        // Keep the JVM alive — consumers run on RabbitMQ's internal threads.
        Thread.currentThread().join();
    }
}
