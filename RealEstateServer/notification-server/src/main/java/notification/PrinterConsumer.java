package notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;

public class PrinterConsumer {

    private final Channel channel;
    private final String queue;
    private final ObjectMapper mapper = new ObjectMapper();

    public PrinterConsumer(Channel channel, String queue) {
        this.channel = channel;
        this.queue = queue;
    }

    public void start() throws Exception {
        DeliverCallback cb = (consumerTag, delivery) -> {
            try {
                JsonNode msg = mapper.readTree(delivery.getBody());
                System.out.println(
                        "================ PURCHASER NOTIFICATION ================\n" +
                        "To:      " + msg.path("purchaserName").asText() +
                            " <" + msg.path("purchaserEmail").asText() + ">  (id "
                            + msg.path("purchaserId").asLong() + ")\n" +
                        "Subject: " + msg.path("subject").asText() + "\n" +
                        "Body:    " + msg.path("body").asText() + "\n" +
                        "Event:   " + msg.path("eventType").asText() +
                            " / property " + msg.path("propertyId").asText() +
                            " / postcode " + msg.path("postcode").asText() + "\n" +
                        "Time:    " + msg.path("timestamp").asText() + "\n" +
                        "========================================================");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                System.err.println("PrinterConsumer failed: " + e.getMessage());
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            }
        };
        channel.basicConsume(queue, false, cb, tag -> {});
    }
}
