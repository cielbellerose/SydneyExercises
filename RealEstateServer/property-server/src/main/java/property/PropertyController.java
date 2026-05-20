package property;

import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PropertyController {

    private final PropertyDAO properties;

    public PropertyController(PropertyDAO properties) {
        this.properties = properties;
    }

    // POST /property
    public void createProperty(Context ctx) {
        Property property = ctx.bodyValidator(Property.class).get();
        if (properties.newProperty(property)) {
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
            properties.incrementViewCount(id);
            ctx.json(property.get());
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
}
