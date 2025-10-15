import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class FingerprintDAO {

    // Save fingerprint with voter details
    public static void saveFingerprint(byte[] fidData, String name, String surname, String idNum) {
        String sql = "INSERT INTO VOTERS (FINGERPRINT, NAME, SURNAME, ID_NUMBER) VALUES (?, ?, ?, ?)";

        Connection conn = Database.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBytes(1, fidData);
            stmt.setString(2, name);
            stmt.setString(3, surname);
            stmt.setString(4, idNum);
            stmt.executeUpdate();
            System.out.println("🧩 Fingerprint and voter details saved to database.");
        } catch (SQLException e) {
            System.out.println("❌ Error saving fingerprint: " + e.getMessage());
        }
    }

    // Retrieve all voters
    public static ResultSet getAllVoters() {
        String sql = "SELECT NAME, SURNAME, ID_NUMBER, FINGERPRINT FROM VOTERS";

        Connection conn = Database.getConnection();
        try {
            Statement stmt = conn.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            return stmt.executeQuery(sql);
        } catch (SQLException e) {
            System.out.println("❌ Error retrieving voters: " + e.getMessage());
            return null;
        }
    }

    // Delete voter by ID number
    public static boolean deleteVoter(String idNumber) {
        String sql = "DELETE FROM VOTERS WHERE ID_NUMBER = ?";
        Connection conn = Database.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, idNumber);
            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("🗑 Voter with ID " + idNumber + " deleted successfully.");
                return true;
            } else {
                System.out.println("⚠️ No voter found with ID " + idNumber + ".");
                return false;
            }

        } catch (SQLException e) {
            System.out.println("❌ Error deleting voter: " + e.getMessage());
            return false;
        }
    }
}
