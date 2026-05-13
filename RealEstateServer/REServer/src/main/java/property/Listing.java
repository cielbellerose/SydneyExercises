package property;

import java.util.List;

public class Listing {
    public long listingId;
    public long propertyId;
    public String listedDate;     // ISO yyyy-mm-dd
    public String status;          // "active" | "sold" | "withdrawn"
    public long currentPrice;      // most recent price; convenience field
    public List<PriceEntry> priceHistory;  // populated by detail endpoint only

    public Listing() {}

    public static class PriceEntry {
        public long price;
        public String effectiveDate;
        public PriceEntry() {}
        public PriceEntry(long price, String effectiveDate) {
            this.price = price;
            this.effectiveDate = effectiveDate;
        }
    }
}
