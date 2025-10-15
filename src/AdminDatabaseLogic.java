
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class AdminDatabaseLogic {

    // Save fingerprint with voter details
    public static void saveFingerprint(Connection conn, byte[] fidData, String name, String surname, String idNum) {
        String sql = "INSERT INTO VOTERS (FINGERPRINT, NAME, SURNAME, ID_NUMBER) VALUES (?, ?, ?, ?)";
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
    public static List<Vector<Object>> getAllVoters(Connection conn) {
        String sql = "SELECT NAME, SURNAME, ID_NUMBER, FINGERPRINT FROM VOTERS";
        List<Vector<Object>> voters = new ArrayList<>();

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getString("NAME"));
                row.add(rs.getString("SURNAME"));
                row.add(rs.getString("ID_NUMBER"));
                row.add(rs.getBytes("FINGERPRINT") != null ? "✅" : "");
                voters.add(row);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error retrieving voters: " + e.getMessage());
        }

        return voters;
    }

    // Delete voter
    public static boolean deleteVoter(Connection conn, String idNumber) {
        String sql = "DELETE FROM VOTERS WHERE ID_NUMBER = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, idNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Error deleting voter: " + e.getMessage());
            return false;
        }
    }

    // Add candidate to multiple tables
    public static boolean addCandidateToBallots(Connection conn, String partyName, String candidateName,
            boolean national, boolean regional, boolean provincial) {
        boolean success = false;
        try {
            if (national) {
                success |= addCandidateToTable(conn, "NationalBallot", partyName, candidateName);
            }
            if (regional) {
                success |= addCandidateToTable(conn, "RegionalBallot", partyName, candidateName);
            }
            if (provincial) {
                success |= addCandidateToTable(conn, "ProvincialBallot", partyName, candidateName);
            }
        } catch (Exception e) {
            System.err.println("❌ Error in addCandidateToBallots: " + e.getMessage());
        }
        return success;
    }

    private static boolean addCandidateToTable(Connection conn, String tableName, String partyName, String candidateName) {
        String sql = "INSERT INTO " + tableName + " (party_name, candidate_name) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, partyName);
            stmt.setString(2, candidateName);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("✅ Added " + candidateName + " to " + tableName);
            }
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("❌ SQL Error adding candidate to " + tableName + ": " + e.getMessage());
            return false;
        }
    }

    public static List<Vector<Object>> getAllCandidatesFromTable(Connection conn, String tableName) {
        String category = tableName.replace("Ballot", "");
        String sql = "SELECT c.party_name, c.candidate_name, "
                + "COALESCE(v.vote_count, 0) AS votes "
                + "FROM " + tableName + " c "
                + "LEFT JOIN ("
                + "    SELECT party_name, COUNT(*) AS vote_count "
                + "    FROM Vote "
                + "    WHERE category = ? "
                + "    GROUP BY party_name"
                + ") v ON c.party_name = v.party_name";

        List<Vector<Object>> candidates = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("party_name"));
                    row.add(rs.getString("candidate_name"));
                    row.add(rs.getInt("votes")); // 0 if no votes
                    candidates.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error retrieving candidates: " + e.getMessage());
        }

        return candidates;
    }

    // Count votes for a party per category
    public static int countVotesForParty(Connection conn, String partyName, String category) {
        String sql = "SELECT COUNT(*) AS total FROM Vote WHERE party_name = ? AND category = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, partyName);
            stmt.setString(2, category);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error counting votes for " + partyName + ": " + e.getMessage());
        }
        return 0;
    }

    // Delete candidate
    public static boolean deleteCandidateFromTable(Connection conn, String tableName, String candidateName) {
        String sql = "DELETE FROM " + tableName + " WHERE candidate_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, candidateName);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("🗑 Deleted " + candidateName + " from " + tableName);
            }
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("❌ Error deleting candidate from " + tableName + ": " + e.getMessage());
            return false;
        }
    }

    public static List<Vector<Object>> getVoteStatistics(Connection conn) {
        List<Vector<Object>> stats = new ArrayList<>();
        String sql = "SELECT c.party_name, "
                + "COALESCE(v.total_votes, 0) AS total_votes, "
                + "COALESCE(v.today_votes, 0) AS votes_today "
                + "FROM ("
                + "    SELECT DISTINCT party_name FROM Vote "
                + "    UNION "
                + "    SELECT DISTINCT party_name FROM NationalBallot "
                + "    UNION "
                + "    SELECT DISTINCT party_name FROM RegionalBallot "
                + "    UNION "
                + "    SELECT DISTINCT party_name FROM ProvincialBallot "
                + ") c "
                + "LEFT JOIN ("
                + "    SELECT party_name, "
                + "    COUNT(*) AS total_votes, "
                + "    SUM(CASE WHEN DATE(timestamp) = CURDATE() THEN 1 ELSE 0 END) AS today_votes "
                + "    FROM Vote "
                + "    GROUP BY party_name"
                + ") v ON c.party_name = v.party_name "
                + "ORDER BY total_votes DESC";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getString("party_name"));
                row.add(rs.getInt("total_votes"));
                row.add(rs.getInt("votes_today"));
                stats.add(row);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error retrieving vote stats: " + e.getMessage());
        }

        return stats;
    }

}
