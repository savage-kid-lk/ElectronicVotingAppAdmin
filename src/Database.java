

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database connection class for Derby.
 * Uses username "letago" and password "#Maureen58".
 */
public class Database {

    private static final String URL = "jdbc:derby://localhost:1527/fingerprint";
    private static final String USER = "letago";
    private static final String PASSWORD = "#Maureen58";
    private static Connection connection = null;

    public static Connection getConnection() {
        if (connection == null) {
            try {
                Class.forName("org.apache.derby.jdbc.ClientDriver");
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("✅ Connected to Derby database as user '" + USER + "'.");
            } catch (ClassNotFoundException e) {
                System.out.println("❌ Derby driver not found: " + e.getMessage());
            } catch (SQLException ex) {
                System.out.println("❌ Database connection failed: " + ex.getMessage());
            }
        }
        return connection;
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("🔒 Database connection closed.");
            } catch (SQLException ex) {
                System.out.println("⚠️ Failed to close DB connection: " + ex.getMessage());
            }
        }
    }
}
