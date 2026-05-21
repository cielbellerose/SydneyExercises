package notification;

import com.fasterxml.jackson.databind.JsonNode;

public final class Formatter {

    private Formatter() {}

    public static String subjectFor(JsonNode event) {
        String type = event.path("eventType").asText();
        String postcode = event.path("postcode").asText();
        return switch (type) {
            case "NEW_LISTING"   -> "New property for sale in postcode " + postcode;
            case "PRICE_CHANGE"  -> "Price change in postcode " + postcode;
            case "STATUS_CHANGE" -> "Status change in postcode " + postcode;
            case "HOT_PROPERTY"  -> "A property in postcode " + postcode + " is hot";
            default              -> "Update in postcode " + postcode;
        };
    }

    public static String bodyFor(JsonNode event) {
        String type = event.path("eventType").asText();
        String propertyId = event.path("propertyId").asText();
        String postcode = event.path("postcode").asText();
        return switch (type) {
            case "NEW_LISTING" ->
                "Property " + propertyId + " in " + postcode + " has just been listed for sale at $"
                    + event.path("price").asLong() + ".";
            case "PRICE_CHANGE" ->
                "Property " + propertyId + " in " + postcode + " has changed price to $"
                    + event.path("price").asLong() + ".";
            case "STATUS_CHANGE" ->
                "Property " + propertyId + " in " + postcode + " status changed: forSale="
                    + event.path("forSale").asBoolean() + ".";
            case "HOT_PROPERTY" ->
                "Property " + propertyId + " in " + postcode + " is hot! View count is now "
                    + event.path("viewCount").asLong() + ".";
            default -> "Update for property " + propertyId + " in " + postcode + ".";
        };
    }
}
