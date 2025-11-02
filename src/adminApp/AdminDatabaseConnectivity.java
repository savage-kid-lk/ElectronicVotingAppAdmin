package adminApp;

import java.sql.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * AdminDatabaseConnectivity connection class for MySQL (Railway).
 * Enhanced with connection validation and keep-alive to prevent timeouts.
 */
public class AdminDatabaseConnectivity {

    private static final String URL = "jdbc:mysql://shortline.proxy.rlwy.net:36648/railway";
    private static final String USER = "root";
    private static final String PASSWORD = "wHwviYYfzHbeerUnyxIyccXUrYgAhzsL";
    private static Connection connection = null;
    private static Timer keepAliveTimer = null;
    private static boolean connectionLost = false;

    /**
     * Returns a database connection with automatic reconnection and keep-alive.
     */
    public static synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !isConnectionValid()) {
                System.out.println("🔄 Establishing new admin database connection...");
                Class.forName("com.mysql.cj.jdbc.Driver");
                
                // Add connection parameters to prevent timeout
                String connectionUrl = URL + "?autoReconnect=true" +
                    "&useSSL=false" +
                    "&verifyServerCertificate=false" +
                    "&useLegacyDatetimeCode=false" +
                    "&serverTimezone=UTC" +
                    "&connectTimeout=30000" +
                    "&socketTimeout=30000" +
                    "&tcpKeepAlive=true";
                
                connection = DriverManager.getConnection(connectionUrl, USER, PASSWORD);
                
                // Configure connection to prevent timeout
                connection.setAutoCommit(true);
                connectionLost = false;
                
                System.out.println("✅ Connected to MySQL database as user '" + USER + "'.");
                
                // Start keep-alive timer if not already running
                startKeepAlive();
            }
        } catch (ClassNotFoundException e) {
            System.err.println("❌ MySQL driver not found: " + e.getMessage());
            connectionLost = true;
        } catch (SQLException ex) {
            System.err.println("❌ Database connection failed: " + ex.getMessage());
            connectionLost = true;
            connection = null;
        }
        return connection;
    }

    /**
     * Validates if the current connection is still active
     */
    private static boolean isConnectionValid() {
        if (connection == null) {
            return false;
        }
        
        try {
            // Try to execute a simple query to test connection
            Statement testStmt = connection.createStatement();
            testStmt.setQueryTimeout(5); // 5 second timeout
            ResultSet rs = testStmt.executeQuery("SELECT 1");
            rs.close();
            testStmt.close();
            connectionLost = false;
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Connection validation failed: " + e.getMessage());
            connectionLost = true;
            return false;
        }
    }

    /**
     * Starts a timer to execute periodic keep-alive queries
     */
    private static void startKeepAlive() {
        // Cancel existing timer if running
        if (keepAliveTimer != null) {
            keepAliveTimer.cancel();
        }
        
        keepAliveTimer = new Timer(true); // Daemon thread
        keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                performKeepAlive();
            }
        }, 240000, 240000); // Run every 4 minutes (240,000 ms)
    }

    /**
     * Executes a simple query to keep the connection alive
     */
    private static void performKeepAlive() {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            if (conn != null && !conn.isClosed()) {
                stmt = conn.createStatement();
                stmt.setQueryTimeout(5);
                rs = stmt.executeQuery("SELECT 1");
                System.out.println("💓 Admin database keep-alive ping executed successfully");
            }
        } catch (SQLException e) {
            System.err.println("❌ Admin keep-alive ping failed: " + e.getMessage());
            // Force reconnection on next getConnection() call
            connection = null;
            connectionLost = true;
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing keep-alive resources: " + e.getMessage());
            }
        }
    }

    /**
     * Gets a validated connection that is guaranteed to be active
     */
    public static synchronized Connection getValidatedConnection() {
        Connection conn = getConnection();
        
        // Double validation
        if (!isConnectionValid()) {
            System.err.println("🔄 Admin connection invalid, forcing reconnection...");
            connection = null; // Force new connection
            conn = getConnection();
        }
        
        return conn;
    }

    /**
     * Check if connection is lost and needs re-authentication
     */
    public static boolean isConnectionLost() {
        return connectionLost;
    }

    /**
     * Set connection lost status
     */
    public static void setConnectionLost(boolean lost) {
        connectionLost = lost;
    }

    /**
     * Force reconnection - used when login is clicked again
     */
    public static synchronized void forceReconnection() {
        System.out.println("🔄 Forcing database reconnection...");
        closeConnection();
        connectionLost = false;
        getConnection();
    }

    /**
     * Check if we should redirect to login due to connection issues
     */
    public static boolean shouldRedirectToLogin() {
        return connectionLost || connection == null || !isConnectionValid();
    }

    /**
     * Closes the database connection and stops keep-alive timer
     */
    public static synchronized void closeConnection() {
        // Stop keep-alive timer
        if (keepAliveTimer != null) {
            keepAliveTimer.cancel();
            keepAliveTimer = null;
            System.out.println("⏹️ Admin keep-alive timer stopped");
        }
        
        // Close connection
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    System.out.println("🔒 Admin database connection closed.");
                }
            } catch (SQLException ex) {
                System.err.println("⚠️ Failed to close admin DB connection: " + ex.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    /**
     * Emergency connection reset - use when connection is stuck
     */
    public static synchronized void resetConnection() {
        System.out.println("🔄 Emergency admin connection reset requested...");
        closeConnection();
        getConnection(); // Re-establish connection
    }

    /**
     * Test connection and return status
     */
    public static String getConnectionStatus() {
        if (connection == null) {
            return "❌ No connection";
        }
        
        try {
            if (connection.isClosed()) {
                return "❌ Connection closed";
            }
            
            if (isConnectionValid()) {
                return "✅ Connection active and valid";
            } else {
                return "⚠️ Connection exists but may be stale";
            }
        } catch (SQLException e) {
            return "❌ Connection error: " + e.getMessage();
        }
    }
}