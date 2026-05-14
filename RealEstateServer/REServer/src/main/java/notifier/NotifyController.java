package notifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.javalin.http.Context;
import property.Property;
import property.PropertyDAO;
import purchaser.Purchaser;
import purchaser.PurchaserDAO;

public class NotifyController {
  private final PurchaserDAO purchasers;
  private final PropertyDAO properties;

  public NotifyController(PurchaserDAO purchasers, PropertyDAO properties) {
    this.purchasers = purchasers;
    this.properties = properties;
  }

  // GET /notify/{purchaserId}
  public Notification notifyPurchaser(Context ctx, String purchaserIdStr) {
    long purchaserId;
    try {
      purchaserId = Long.parseLong(purchaserIdStr);
    } catch (NumberFormatException e) {
      ctx.status(400).result("Invalid purchaser id");
      return null;
    }

    Purchaser purchaser = purchasers.getPurchaser(purchaserId);
    if (purchaser == null) {
      ctx.status(404).result("Purchaser not found");
      return null;
    }
    if (purchaser.interests.isEmpty()) {
      ctx.status(404).result("No interests found");
      return null;
    }

    HashMap<String, List<Notification.PropertyMatch>> results = new HashMap<>();
    for (String postcode : purchaser.interests) {
      List<Notification.PropertyMatch> matches = new ArrayList<>();
      for (Property p : properties.getPropertiesByPostCode(postcode)) {
//        if (!p.forSale) continue;
        long price;
        try {
          price = Long.parseLong(p.propertyPrice);
        } catch (NumberFormatException e) {
          continue;
        }
        matches.add(new Notification.PropertyMatch(p.propertyID, price));
      }
      if (!matches.isEmpty()) {
        results.put(postcode, matches);
      }
    }

    Notification notification = new Notification(
        String.valueOf(purchaser.purchaserId),
        purchaser.email,
        purchaser.interests);
    notification.setPostcodeResults(results);
    ctx.json(notification);
    return notification;
  }
  // Example:
  // 02215
  //    4 $9999
  //    23 $16263
  //    5 $1926
  // 01159
  //    1 $22
  //    6 $1926

}
