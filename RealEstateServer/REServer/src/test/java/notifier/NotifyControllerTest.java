package notifier;

import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import property.Property;
import property.PropertyDAO;
import purchaser.Purchaser;
import purchaser.PurchaserDAO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifyControllerTest {

  private PurchaserDAO purchasers;
  private PropertyDAO properties;
  private NotifyController controller;
  private Context ctx;

  @BeforeEach
  void setUp() {
    purchasers = mock(PurchaserDAO.class);
    properties = mock(PropertyDAO.class);
    controller = new NotifyController(purchasers, properties);
    // RETURNS_SELF makes ctx.status(...).result(...) chain work without extra stubbing.
    ctx = mock(Context.class, Answers.RETURNS_SELF);
  }

  @Test
  void returnsNotificationMatchingExpectedShape() {
    Purchaser p = new Purchaser();
    p.purchaserId = 1L;
    p.email = "abc";
    p.name = "guy";
    p.interests = Arrays.asList("2325", "9999");
    when(purchasers.getPurchaser(1L)).thenReturn(p);

    Property a = new Property("122", "2325", "110000");
    Property b = new Property("123", "2325", "600490");
    Property c = new Property("124", "2325", "630000");
    when(properties.getPropertiesByPostCode("2325")).thenReturn(Arrays.asList(a, b, c));
    when(properties.getPropertiesByPostCode("9999")).thenReturn(Collections.emptyList());

    Notification n = controller.notifyPurchaser(ctx, "1");

    assertNotNull(n);
    assertEquals("1", n.getPurchaserId());
    assertEquals("abc", n.getPurchaserEmail());
    assertEquals(Arrays.asList("2325", "9999"), n.getPostCodes());

    List<Notification.PropertyMatch> matches = n.getPostcodeResults().get("2325");
    assertNotNull(matches);
    assertEquals(3, matches.size());
    assertEquals("122", matches.get(0).propertyID);
    assertEquals(110000L, matches.get(0).salePrice);
    assertEquals("123", matches.get(1).propertyID);
    assertEquals(600490L, matches.get(1).salePrice);
    assertEquals("124", matches.get(2).propertyID);
    assertEquals(630000L, matches.get(2).salePrice);

    // Empty-match postcodes are omitted from postcodeResults.
    assertFalse(n.getPostcodeResults().containsKey("9999"));

    verify(ctx).json(n);
  }

  @Test
  void invalidIdReturns400() {
    Notification n = controller.notifyPurchaser(ctx, "not-a-number");
    assertNull(n);
    verify(ctx).status(400);
  }

  @Test
  void unknownPurchaserReturns404() {
    when(purchasers.getPurchaser(42L)).thenReturn(null);
    Notification n = controller.notifyPurchaser(ctx, "42");
    assertNull(n);
    verify(ctx).status(404);
  }

  @Test
  void emptyInterestsReturns404() {
    Purchaser p = new Purchaser();
    p.purchaserId = 5L;
    p.email = "x@y";
    p.interests = Collections.emptyList();
    when(purchasers.getPurchaser(5L)).thenReturn(p);

    Notification n = controller.notifyPurchaser(ctx, "5");
    assertNull(n);
    verify(ctx).status(404);
  }

  @Test
  void skipsPropertiesWithNonNumericPrice() {
    Purchaser p = new Purchaser();
    p.purchaserId = 1L;
    p.email = "abc";
    p.interests = List.of("2325");
    when(purchasers.getPurchaser(1L)).thenReturn(p);

    Property good = new Property("100", "2325", "250000");
    Property bad = new Property("101", "2325", "not-a-price");
    when(properties.getPropertiesByPostCode("2325")).thenReturn(Arrays.asList(good, bad));

    Notification n = controller.notifyPurchaser(ctx, "1");
    List<Notification.PropertyMatch> matches = n.getPostcodeResults().get("2325");
    assertEquals(1, matches.size());
    assertEquals("100", matches.get(0).propertyID);
    assertEquals(250000L, matches.get(0).salePrice);
  }
}
