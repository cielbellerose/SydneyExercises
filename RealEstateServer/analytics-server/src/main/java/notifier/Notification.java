package notifier;

import java.util.HashMap;
import java.util.List;

public class Notification {
  private String purchaserId;
  private String purchaserEmail;
  private List<String> postCodes;
  private HashMap<String, List<PropertyMatch>> postcodeResults;


  public Notification() {}

  public Notification(String purchaserId, String purchaserEmail, List<String> postCodes) {
    this.purchaserId = purchaserId;
    this.purchaserEmail = purchaserEmail;
    this.postCodes = postCodes;
  }

  public static class PropertyMatch {
    public String propertyID;
    public long salePrice;

    public PropertyMatch() {}

    public PropertyMatch(String propertyID, long salePrice) {
      this.propertyID = propertyID;
      this.salePrice = salePrice;
    }
  }

  public String getPurchaserId() { return purchaserId; }
  public String getPurchaserEmail() { return purchaserEmail; }
  public List<String> getPostCodes() { return postCodes; }
  public HashMap<String, List<PropertyMatch>> getPostcodeResults() { return postcodeResults; }

  public void setPurchaserId(String purchaserId) {
    this.purchaserId = purchaserId;
  }

  public void setPurchaserEmail(String purchaserEmail) {
    this.purchaserEmail = purchaserEmail;
  }

  public void setPostcodeResults(HashMap<String, List<PropertyMatch>> postcodeResults) {
    this.postcodeResults = postcodeResults;
  }
}
