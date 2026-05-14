package notifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class NotifyDAO {
  private static final String DB_URL  = "jdbc:postgresql://localhost:8000/realestate";
  private static final String DB_USER = "realestate";
  private static final String DB_PASS = "realestate";

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
  }
}
