package adminApp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database connection class for MySQL (Railway).
 */
public class Database {

    private static final String URL = "jdbc:mysql://shortline.proxy.rlwy.net:36648/railway";
    private static final String USER = "root";
    private static final String PASSWORD = "wHwviYYfzHbeerUnyxIyccXUrYgAhzsL";
    private static Connection connection = null;

    public static Connection getConnection() {
        if (connection == null) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver"); // MySQL driver
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("✅ Connected to MySQL database as user '" + USER + "'.");
            } catch (ClassNotFoundException e) {
                System.out.println("❌ MySQL driver not found: " + e.getMessage());
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
