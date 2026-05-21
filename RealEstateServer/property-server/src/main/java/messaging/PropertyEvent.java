package messaging;

import java.time.Instant;

public class PropertyEvent {

    public enum Type { NEW_LISTING, PRICE_CHANGE, STATUS_CHANGE, HOT_PROPERTY }

    public String eventType;
    public String propertyId;
    public String postcode;
    public Long price;
    public Boolean forSale;
    public Long viewCount;
    public String timestamp;

    public PropertyEvent() {}

    private PropertyEvent(Type type, String propertyId, String postcode) {
        this.eventType = type.name();
        this.propertyId = propertyId;
        this.postcode = postcode;
        this.timestamp = Instant.now().toString();
    }

    public static PropertyEvent newListing(String propertyId, String postcode, long price) {
        PropertyEvent e = new PropertyEvent(Type.NEW_LISTING, propertyId, postcode);
        e.price = price;
        e.forSale = true;
        return e;
    }

    public static PropertyEvent priceChange(String propertyId, String postcode, long price) {
        PropertyEvent e = new PropertyEvent(Type.PRICE_CHANGE, propertyId, postcode);
        e.price = price;
        return e;
    }

    public static PropertyEvent statusChange(String propertyId, String postcode, boolean forSale) {
        PropertyEvent e = new PropertyEvent(Type.STATUS_CHANGE, propertyId, postcode);
        e.forSale = forSale;
        return e;
    }

    public static PropertyEvent hotProperty(String propertyId, String postcode, long viewCount) {
        PropertyEvent e = new PropertyEvent(Type.HOT_PROPERTY, propertyId, postcode);
        e.viewCount = viewCount;
        return e;
    }
}
