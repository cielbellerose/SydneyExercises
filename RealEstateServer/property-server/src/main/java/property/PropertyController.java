package property;

import io.javalin.http.Context;
import messaging.EventPublisher;
import messaging.PropertyEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PropertyController {

    private final PropertyDAO properties;
    private final EventPublisher events;

    public PropertyController(PropertyDAO properties, EventPublisher events) {
        this.properties = properties;
        this.events = events;
    }

    // POST /property
    public void createProperty(Context ctx) {
        Property property = ctx.bodyValidator(Property.class).get();
        if (properties.newProperty(property)) {
            if (property.forSale) {
                long price = parsePrice(property.propertyPrice);
                events.publish(PropertyEvent.newListing(property.propertyID, property.postcode, price));
            }
            ctx.status(201).json(Map.of("status", "created", "propertyId", property.propertyID));
        } else {
            ctx.status(400).json(Map.of("error", "Failed to add property"));
        }
    }

    // GET /properties (optional minPrice/maxPrice query params)
    public void getAllProperties(Context ctx) {
        String minParam = ctx.queryParam("minPrice");
        String maxParam = ctx.queryParam("maxPrice");

        List<Property> allProperties;
        if (minParam != null || maxParam != null) {
            long min = minParam != null ? Long.parseLong(minParam) : Long.MIN_VALUE;
            long max = maxParam != null ? Long.parseLong(maxParam) : Long.MAX_VALUE;
            allProperties = properties.getPropertiesByPriceRange(min, max);
        } else {
            allProperties = properties.getAllProperties();
        }

        if (allProperties.isEmpty()) {
            ctx.status(404).json(Map.of("error", "No Properties Found"));
        } else {
            ctx.json(allProperties);
        }
    }

    // GET /property/{propertyID}
    public void getPropertyByID(Context ctx, String id) {
        Optional<Property> property = properties.getPropertyById(id);
        if (property.isPresent()) {
            Property p = property.get();
            properties.incrementViewCount(id);
            if (p.forSale) {
                long views = properties.getViewCount(id).orElse(0L);
                events.publish(PropertyEvent.hotProperty(id, p.postcode, views));
            }
            ctx.json(p);
        } else {
            ctx.status(404).json(Map.of("error", "Property not found"));
        }
    }

    // GET /property/{propertyID}/views
    public void getPropertyViews(Context ctx, String id) {
        Optional<Long> views = properties.getViewCount(id);
        if (views.isPresent()) {
            ctx.json(Map.of("propertyId", id, "views", views.get()));
        } else {
            ctx.status(404).json(Map.of("error", "Property not found"));
        }
    }

    // GET /property/postcode/{postcodeID}
    public void findPropertyByPostCode(Context ctx, String postCode) {
        List<Property> result = properties.getPropertiesByPostCode(postCode);
        if (result.isEmpty()) {
            ctx.status(404).json(Map.of("error", "No properties for postcode found"));
        } else {
            ctx.json(result);
        }
    }

    // POST /property/{propertyID}/forSale  body: { "forSale": true|false }
    public void setForSale(Context ctx, String id) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Object flag = body.get("forSale");
        if (!(flag instanceof Boolean)) {
            ctx.status(400).json(Map.of("error", "Body must include boolean 'forSale'"));
            return;
        }
        boolean forSale = (Boolean) flag;
        Optional<Property> existing = properties.getPropertyById(id);
        if (existing.isEmpty()) {
            ctx.status(404).json(Map.of("error", "Property not found"));
            return;
        }
        if (properties.setForSale(id, forSale)) {
            events.publish(PropertyEvent.statusChange(id, existing.get().postcode, forSale));
            ctx.json(Map.of("propertyId", id, "forSale", forSale));
        } else {
            ctx.status(400).json(Map.of("error", "Failed to update forSale"));
        }
    }

    private static long parsePrice(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }
}
