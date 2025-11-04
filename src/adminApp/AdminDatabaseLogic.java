package adminApp;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.JOptionPane;

public class AdminDatabaseLogic {

    private static boolean allFieldsFilled(String name, String surname, String idNum) {
        return !(name == null || name.isEmpty()
                || surname == null || surname.isEmpty()
                || idNum == null || idNum.isEmpty());
    }

    public static boolean isValidSouthAfricanID(String id) {
        if (id == null || !id.matches("\\d{13}")) {
            return false;
        }

        String birth = id.substring(0, 6);
        LocalDate dateOfBirth = parseSouthAfricanIDDate(birth);
        if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now())) {
            return false;
        }

        char citizenship = id.charAt(10);
        if (citizenship != '0' && citizenship != '1') {
            return false;
        }

        return luhnCheck(id);
    }

    private static LocalDate parseSouthAfricanIDDate(String birth) {
        try {
            int year = Integer.parseInt(birth.substring(0, 2));
            int month = Integer.parseInt(birth.substring(2, 4));
            int day = Integer.parseInt(birth.substring(4, 6));

            int currentYearTwoDigits = LocalDate.now().getYear() % 100;
            int century = (year > currentYearTwoDigits) ? 1900 : 2000;

            int fullYear = century + year;

            return LocalDate.of(fullYear, month, day);
        } catch (Exception e) {
            return null; 
        }
    }

    private static boolean luhnCheck(String id) {
        int sum = 0;
        boolean alternate = false;
        for (int i = id.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(id.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    private static boolean idExists(Connection conn, String table, String idNum) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + table + " WHERE ID_NUMBER = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, idNum);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    public static boolean saveVoter(Connection conn, byte[] fidData, String name, String surname, String idNum) {
        if (!allFieldsFilled(name, surname, idNum)) {
            JOptionPane.showMessageDialog(null, "Please fill in all fields.");
            return false;
        }
        if (!isValidSouthAfricanID(idNum)) {
            JOptionPane.showMessageDialog(null, "Invalid South African ID number.");
            return false;
        }
        try {
            if (idExists(conn, "VOTERS", idNum)) {
                JOptionPane.showMessageDialog(null, "Voter with this ID already exists.");
                return false;
            }
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Add this voter?\nName: " + name + " " + surname + "\nID: " + idNum,
                    "Confirm Add Voter", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return false;
            }
            String sql = "INSERT INTO VOTERS (FINGERPRINT, NAME, SURNAME, ID_NUMBER, has_voted) VALUES (?, ?, ?, ?, FALSE)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBytes(1, fidData);
                stmt.setString(2, name);
                stmt.setString(3, surname);
                stmt.setString(4, idNum);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Voter added successfully!");
                return true;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage());
            return false;
        }
    }

    public static boolean saveAdmin(Connection conn, byte[] fidData, String name, String surname, String idNum) {
        if (!allFieldsFilled(name, surname, idNum)) {
            JOptionPane.showMessageDialog(null, "Please fill in all fields.");
            return false;
        }
        if (!isValidSouthAfricanID(idNum)) {
            JOptionPane.showMessageDialog(null, "Invalid South African ID number.");
            return false;
        }
        try {
            if (idExists(conn, "Admins", idNum)) {
                JOptionPane.showMessageDialog(null, "Admin with this ID already exists.");
                return false;
            }
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Add this admin?\nName: " + name + " " + surname + "\nID: " + idNum,
                    "Confirm Add Admin", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return false;
            }
            String sql = "INSERT INTO Admins (FINGERPRINT, NAME, SURNAME, ID_NUMBER) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBytes(1, fidData);
                stmt.setString(2, name);
                stmt.setString(3, surname);
                stmt.setString(4, idNum);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Admin added successfully!");
                return true;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage());
            return false;
        }
    }

    public static List<Vector<Object>> getAllVoters(Connection conn) {
        conn = AdminDatabaseConnectivity.getValidatedConnection();
        List<Vector<Object>> voters = new ArrayList<>();
        String sql = "SELECT NAME, SURNAME, ID_NUMBER, FINGERPRINT, has_voted FROM VOTERS";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getString("NAME"));
                row.add(rs.getString("SURNAME"));
                row.add(rs.getString("ID_NUMBER"));
                row.add(rs.getBytes("FINGERPRINT") != null ? "Captured" : "Not Captured");
                row.add(rs.getBoolean("has_voted") ? "Yes" : "No");
                voters.add(row);
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return voters;
    }

    public static List<Vector<Object>> searchVoters(Connection conn, String searchTerm) {
        conn = AdminDatabaseConnectivity.getValidatedConnection();
        List<Vector<Object>> voters = new ArrayList<>();
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAllVoters(conn);
        }
        
        String cleanSearch = searchTerm.trim().toLowerCase();
        String sql = "SELECT NAME, SURNAME, ID_NUMBER, FINGERPRINT, has_voted FROM VOTERS " +
                    "WHERE LOWER(NAME) LIKE ? OR LOWER(SURNAME) LIKE ? OR ID_NUMBER LIKE ? OR " +
                    "LOWER(CONCAT(NAME, ' ', SURNAME)) LIKE ? OR LOWER(CONCAT(SURNAME, ' ', NAME)) LIKE ? OR " +
                    "LOWER(CONCAT(NAME, SURNAME)) LIKE ? OR LOWER(CONCAT(SURNAME, NAME)) LIKE ? OR " +
                    "(has_voted = ? AND ? = 'voted') OR (has_voted = ? AND ? = 'not voted') OR " +
                    "(has_voted = ? AND ? = 'yes') OR (has_voted = ? AND ? = 'no') OR " +
                    "LOWER(NAME) LIKE ? OR LOWER(SURNAME) LIKE ? OR " +
                    "LOWER(ID_NUMBER) LIKE ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String searchPattern = "%" + cleanSearch + "%";
            String[] terms = cleanSearch.split("\\s+");
            
            for (int i = 1; i <= 7; i++) {
                stmt.setString(i, searchPattern);
            }
            
            boolean isVoted = cleanSearch.equals("voted") || cleanSearch.equals("yes");
            boolean isNotVoted = cleanSearch.equals("not voted") || cleanSearch.equals("no");
            
            stmt.setBoolean(8, true);
            stmt.setString(9, cleanSearch);
            stmt.setBoolean(10, false);
            stmt.setString(11, cleanSearch);
            stmt.setBoolean(12, true);
            stmt.setString(13, cleanSearch);
            stmt.setBoolean(14, false);
            stmt.setString(15, cleanSearch);
            
            if (terms.length >= 2) {
                stmt.setString(16, "%" + terms[0] + "%");
                stmt.setString(17, "%" + terms[1] + "%");
                stmt.setString(18, "%" + cleanSearch + "%");
            } else {
                stmt.setString(16, searchPattern);
                stmt.setString(17, searchPattern);
                stmt.setString(18, searchPattern);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("NAME"));
                    row.add(rs.getString("SURNAME"));
                    row.add(rs.getString("ID_NUMBER"));
                    row.add(rs.getBytes("FINGERPRINT") != null ? "Captured" : "Not Captured");
                    row.add(rs.getBoolean("has_voted") ? "Yes" : "No");
                    voters.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Search error: " + e.getMessage());
        }
        return voters;
    }

    public static List<Vector<Object>> searchCandidates(Connection conn, String searchTerm) {
        List<Vector<Object>> allCandidates = new ArrayList<>();
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return allCandidates;
        }
        
        String cleanSearch = searchTerm.trim().toLowerCase();
        String searchPattern = "%" + cleanSearch + "%";
        
        String nationalSql = "SELECT party_name, candidate_name, 'National' as ballot_type, " +
                           "COALESCE((SELECT COUNT(DISTINCT voter_id_number) FROM Votes WHERE party_name = nb.party_name AND category = 'National'), 0) as votes " +
                           "FROM NationalBallot nb " +
                           "WHERE LOWER(party_name) LIKE ? OR LOWER(candidate_name) LIKE ? OR " +
                           "LOWER(CONCAT(party_name, ' ', candidate_name)) LIKE ? OR " +
                           "LOWER(CONCAT(candidate_name, ' ', party_name)) LIKE ?";
        
        String regionalSql = "SELECT party_name, candidate_name, CONCAT('Regional - ', region) as ballot_type, " +
                           "COALESCE((SELECT COUNT(DISTINCT voter_id_number) FROM Votes WHERE party_name = rb.party_name AND category = 'Regional'), 0) as votes " +
                           "FROM RegionalBallot rb " +
                           "WHERE LOWER(party_name) LIKE ? OR LOWER(candidate_name) LIKE ? OR " +
                           "LOWER(region) LIKE ? OR " +
                           "LOWER(CONCAT(party_name, ' ', candidate_name)) LIKE ? OR " +
                           "LOWER(CONCAT(candidate_name, ' ', party_name)) LIKE ?";
        
        String provincialSql = "SELECT party_name, candidate_name, CONCAT('Provincial - ', province) as ballot_type, " +
                             "COALESCE((SELECT COUNT(DISTINCT voter_id_number) FROM Votes WHERE party_name = pb.party_name AND category = 'Provincial'), 0) as votes " +
                             "FROM ProvincialBallot pb " +
                             "WHERE LOWER(party_name) LIKE ? OR LOWER(candidate_name) LIKE ? OR " +
                             "LOWER(province) LIKE ? OR " +
                             "LOWER(CONCAT(party_name, ' ', candidate_name)) LIKE ? OR " +
                             "LOWER(CONCAT(candidate_name, ' ', party_name)) LIKE ?";
        
        try {
            try (PreparedStatement stmt = conn.prepareStatement(nationalSql)) {
                for (int i = 1; i <= 4; i++) {
                    stmt.setString(i, searchPattern);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        row.add(rs.getString("party_name"));
                        row.add(rs.getString("candidate_name"));
                        row.add(rs.getString("ballot_type"));
                        row.add(rs.getInt("votes"));
                        allCandidates.add(row);
                    }
                }
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(regionalSql)) {
                for (int i = 1; i <= 5; i++) {
                    stmt.setString(i, searchPattern);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        row.add(rs.getString("party_name"));
                        row.add(rs.getString("candidate_name"));
                        row.add(rs.getString("ballot_type"));
                        row.add(rs.getInt("votes"));
                        allCandidates.add(row);
                    }
                }
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(provincialSql)) {
                for (int i = 1; i <= 5; i++) {
                    stmt.setString(i, searchPattern);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        row.add(rs.getString("party_name"));
                        row.add(rs.getString("candidate_name"));
                        row.add(rs.getString("ballot_type"));
                        row.add(rs.getInt("votes"));
                        allCandidates.add(row);
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Candidate search error: " + e.getMessage());
        }
        
        return allCandidates;
    }

    public static boolean updateVoterFingerprint(Connection conn, String idNumber, byte[] fingerprintData) {
        conn = AdminDatabaseConnectivity.getValidatedConnection();
        String sql = "UPDATE VOTERS SET FINGERPRINT = ? WHERE ID_NUMBER = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBytes(1, fingerprintData);
            stmt.setString(2, idNumber);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error updating fingerprint: " + e.getMessage());
            return false;
        }
    }
    public static boolean deleteVoter(Connection conn, String idNumber) {
        conn = AdminDatabaseConnectivity.getValidatedConnection();
        try {
            conn.setAutoCommit(false);

            int confirm = JOptionPane.showConfirmDialog(null,
                    "<html><b>WARNING: This will permanently delete ALL data for this voter:</b><br><br>"
                    + "• Voter registration details<br>"
                    + "• All votes cast by this voter<br>"
                    + "• All fraud attempts recorded for this voter<br><br>"
                    + "This action cannot be undone!<br><br>"
                    + "Are you sure you want to proceed?</html>",
                    "Confirm Complete Voter Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                conn.setAutoCommit(true);
                return false;
            }

            boolean success = true;
            int totalRecordsDeleted = 0;

            String deleteVotesSql = "DELETE FROM Votes WHERE voter_id_number = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteVotesSql)) {
                stmt.setString(1, idNumber);
                int votesDeleted = stmt.executeUpdate();
                totalRecordsDeleted += votesDeleted;
                System.out.println("Deleted " + votesDeleted + " votes for voter: " + idNumber);
            }

            String deleteFraudSql = "DELETE FROM FraudAttempts WHERE voter_id_number = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteFraudSql)) {
                stmt.setString(1, idNumber);
                int fraudDeleted = stmt.executeUpdate();
                totalRecordsDeleted += fraudDeleted;
                System.out.println("Deleted " + fraudDeleted + " fraud attempts for voter: " + idNumber);
            }

            String deleteVoterSql = "DELETE FROM VOTERS WHERE ID_NUMBER = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteVoterSql)) {
                stmt.setString(1, idNumber);
                int voterDeleted = stmt.executeUpdate();

                if (voterDeleted > 0) {
                    totalRecordsDeleted += voterDeleted;
                    conn.commit();

                    System.out.println("Successfully deleted voter " + idNumber
                            + " and " + totalRecordsDeleted + " associated records");

                    JOptionPane.showMessageDialog(null,
                            "<html><b>Voter completely deleted!</b><br><br>"
                            + "Removed from system:<br>"
                            + "• Voter registration<br>"
                            + "• All associated votes<br>"
                            + "• All fraud attempt records<br><br>"
                            + "Total records removed: " + totalRecordsDeleted + "</html>");
                    return true;
                } else {
                    conn.rollback();
                    JOptionPane.showMessageDialog(null, "Voter not found with ID: " + idNumber);
                    return false;
                }
            }

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error during rollback: " + rollbackEx.getMessage());
            }
            JOptionPane.showMessageDialog(null, "Database error during voter deletion: " + e.getMessage());
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                System.err.println("Error resetting auto-commit: " + ex.getMessage());
            }
        }
    }

    public static boolean resetVoterVotingStatus(Connection conn, String idNumber) {
        try {
            conn.setAutoCommit(false);

            int confirm = JOptionPane.showConfirmDialog(null,
                    "<html>Reset voting status for voter " + idNumber + "?<br><br>"
                    + "This will:<br>"
                    + "• Set has_voted to FALSE<br>"
                    + "• Delete all votes cast by this voter<br>"
                    + "• Allow the voter to vote again<br><br>"
                    + "Are you sure?</html>",
                    "Confirm Reset Voting Status",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                conn.setAutoCommit(true);
                return false;
            }

            String deleteVotesSql = "DELETE FROM Votes WHERE voter_id_number = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteVotesSql)) {
                stmt.setString(1, idNumber);
                int votesDeleted = stmt.executeUpdate();
                System.out.println("Deleted " + votesDeleted + " votes for voter: " + idNumber);
            }

            String resetVoterSql = "UPDATE VOTERS SET has_voted = FALSE WHERE ID_NUMBER = ?";
            try (PreparedStatement stmt = conn.prepareStatement(resetVoterSql)) {
                stmt.setString(1, idNumber);
                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected > 0) {
                    conn.commit();
                    JOptionPane.showMessageDialog(null,
                            "<html><b>Voting status reset successfully!</b><br><br>"
                            + "Voter " + idNumber + " can now vote again as a new voter.</html>");
                    return true;
                } else {
                    conn.rollback();
                    JOptionPane.showMessageDialog(null, "Voter not found with ID: " + idNumber);
                    return false;
                }
            }

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error during rollback: " + rollbackEx.getMessage());
            }
            JOptionPane.showMessageDialog(null, "Database error resetting voting status: " + e.getMessage());
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                System.err.println("Error resetting auto-commit: " + ex.getMessage());
            }
        }
    }

    public static boolean addCandidateToBallots(Connection conn, String partyName, String candidateName,
            boolean national, boolean regional, boolean provincial, byte[] candidateImage,
            byte[] partyLogo, boolean isIndependent, String value) {

        boolean success = false;
        boolean anyAdded = false;

        if (isIndependent) {
            partyName = "Independent";
            partyLogo = null;
        }

        try {
            if (national) {
                boolean added = addCandidateToTable(conn, "NationalBallot", partyName, candidateName, candidateImage, partyLogo, null);
                success |= added;
                anyAdded |= added;
            }
            if (regional) {
                boolean added = addCandidateToTable(conn, "RegionalBallot", partyName, candidateName, candidateImage, partyLogo, value);
                success |= added;
                anyAdded |= added;
            }
            if (provincial) {
                boolean added = addCandidateToTable(conn, "ProvincialBallot", partyName, candidateName, candidateImage, partyLogo, value);
                success |= added;
                anyAdded |= added;
            }

            if (anyAdded) {
                JOptionPane.showMessageDialog(null, "Candidate added successfully!");
            } else {
                JOptionPane.showMessageDialog(null, "Failed to add candidate to any ballot.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error adding candidate: " + e.getMessage());
        }

        return success;
    }

    private static boolean addCandidateToTable(Connection conn, String tableName, String partyName, String candidateName,
            byte[] candidateImage, byte[] partyLogo, String value) {

        String sql;

        if (tableName.equals("RegionalBallot")) {
            sql = "INSERT INTO " + tableName + " (party_name, candidate_name, candidate_image, party_logo, region) VALUES (?, ?, ?, ?, ?)";
        } else if (tableName.equals("ProvincialBallot")) {
            sql = "INSERT INTO " + tableName + " (party_name, candidate_name, candidate_image, party_logo, province) VALUES (?, ?, ?, ?, ?)";
        } else {
            sql = "INSERT INTO " + tableName + " (party_name, candidate_name, candidate_image, party_logo) VALUES (?, ?, ?, ?)";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, partyName);
            stmt.setString(2, candidateName);
            stmt.setBytes(3, candidateImage);

            if (tableName.equals("NationalBallot")) {
                stmt.setBytes(4, partyLogo);
            } else {
                stmt.setBytes(4, partyLogo);
                stmt.setString(5, value);
            }

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    public static List<Vector<Object>> getAllCandidatesFromTable(Connection conn, String tableName) {
        String category = tableName.replace("Ballot", "");
        String sql = "SELECT c.party_name, c.candidate_name, COALESCE(v.vote_count, 0) AS votes "
                + "FROM " + tableName + " c "
                + "LEFT JOIN (SELECT party_name, COUNT(DISTINCT voter_id_number) AS vote_count FROM Votes WHERE category = ? GROUP BY party_name) v "
                + "ON c.party_name = v.party_name";

        List<Vector<Object>> candidates = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("party_name"));
                    row.add(rs.getString("candidate_name"));
                    row.add(rs.getInt("votes"));
                    candidates.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return candidates;
    }

    public static boolean deleteCandidateFromTable(Connection conn, String tableName, String partyName, String candidateName) {
        String category = "";

        switch (tableName.toLowerCase()) {
            case "nationalballot":
                category = "National";
                break;
            case "regionalballot":
                category = "Regional";
                break;
            case "provincialballot":
                category = "Provincial";
                break;
            default:
                System.err.println("Unknown table name: " + tableName);
                return false;
        }

        try {
            conn.setAutoCommit(false);

            String deleteCandidateQuery = "DELETE FROM " + tableName + " WHERE candidate_name = ? AND party_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteCandidateQuery)) {
                ps.setString(1, candidateName);
                ps.setString(2, partyName);
                ps.executeUpdate();
            }

            String deleteVotesQuery = "DELETE FROM Votes WHERE party_name = ? AND category = ?";
            try (PreparedStatement psVotes = conn.prepareStatement(deleteVotesQuery)) {
                psVotes.setString(1, partyName);
                psVotes.setString(2, category);
                psVotes.executeUpdate();
            }

            conn.commit();
            JOptionPane.showMessageDialog(null, "Candidate and related votes deleted successfully!");
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            JOptionPane.showMessageDialog(null, "Failed to delete candidate: " + e.getMessage());
            return false;

        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static List<Vector<Object>> getVoteStatistics(Connection conn) {
        List<Vector<Object>> stats = new ArrayList<>();
        String sql = "SELECT c.party_name, " +
                     "COALESCE(COUNT(DISTINCT v.voter_id_number), 0) AS total_votes, " +
                     "COALESCE(SUM(CASE WHEN DATE(v.vote_timestamp) = CURDATE() THEN 1 ELSE 0 END), 0) AS votes_today_raw " +
                     "FROM (SELECT DISTINCT party_name FROM Votes " +
                     "      UNION SELECT DISTINCT party_name FROM NationalBallot " +
                     "      UNION SELECT DISTINCT party_name FROM RegionalBallot " +
                     "      UNION SELECT DISTINCT party_name FROM ProvincialBallot) c " +
                     "LEFT JOIN (SELECT party_name, voter_id_number, vote_timestamp " +
                     "           FROM Votes GROUP BY party_name, voter_id_number, vote_timestamp) v " +
                     "ON c.party_name = v.party_name " +
                     "GROUP BY c.party_name " +
                     "ORDER BY total_votes DESC";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getString("party_name"));
                row.add(rs.getInt("total_votes")); // This now counts unique voters per party
                row.add(rs.getInt("votes_today_raw")); // Note: This still needs adjustment for today's votes
                stats.add(row);
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return stats;
    }

    public static void updateVoter(Connection conn, String idNumber, int column, String newValue) {
        String columnName = column == 0 ? "NAME" : "SURNAME";
        String sql = "UPDATE VOTERS SET " + columnName + " = ? WHERE ID_NUMBER = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newValue);
            stmt.setString(2, idNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update voter: " + e.getMessage());
        }
    }

    public static void updateCandidate(Connection conn, String tableName, int column,
        String newValue, String oldParty, String oldCandidate, String regionOrProvince) {
    
    // Disable party name editing - only allow candidate name and image updates
    if (column == 0) { // Party name column
        System.out.println("Party name editing is disabled");
        return;
    }
    
    String columnName = "candidate_name";
    String sql;

    if (tableName.equalsIgnoreCase("RegionalBallot")) {
        sql = "UPDATE RegionalBallot SET " + columnName + " = ? WHERE party_name = ? AND candidate_name = ? AND region = ?";
    } else if (tableName.equalsIgnoreCase("ProvincialBallot")) {
        sql = "UPDATE ProvincialBallot SET " + columnName + " = ? WHERE party_name = ? AND candidate_name = ? AND province = ?";
    } else {
        sql = "UPDATE NationalBallot SET " + columnName + " = ? WHERE party_name = ? AND candidate_name = ?";
    }

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, newValue);
        stmt.setString(2, oldParty);
        stmt.setString(3, oldCandidate);
        if (tableName.equalsIgnoreCase("RegionalBallot") || tableName.equalsIgnoreCase("ProvincialBallot")) {
            stmt.setString(4, regionOrProvince);
        }
        stmt.executeUpdate();
    } catch (SQLException e) {
        System.err.println("Failed to update candidate in " + tableName + ": " + e.getMessage());
    }
}

public static boolean updateCandidateImage(Connection conn, String tableName, String partyName, 
        String candidateName, byte[] candidateImage, String regionOrProvince) {
    
    String sql;
    
    if (tableName.equalsIgnoreCase("RegionalBallot")) {
        sql = "UPDATE RegionalBallot SET candidate_image = ? WHERE party_name = ? AND candidate_name = ? AND region = ?";
    } else if (tableName.equalsIgnoreCase("ProvincialBallot")) {
        sql = "UPDATE ProvincialBallot SET candidate_image = ? WHERE party_name = ? AND candidate_name = ? AND province = ?";
    } else {
        sql = "UPDATE NationalBallot SET candidate_image = ? WHERE party_name = ? AND candidate_name = ?";
    }

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setBytes(1, candidateImage);
        stmt.setString(2, partyName);
        stmt.setString(3, candidateName);
        if (tableName.equalsIgnoreCase("RegionalBallot") || tableName.equalsIgnoreCase("ProvincialBallot")) {
            stmt.setString(4, regionOrProvince);
        }
        return stmt.executeUpdate() > 0;
    } catch (SQLException e) {
        System.err.println("Failed to update candidate image in " + tableName + ": " + e.getMessage());
        return false;
    }
}

    public static List<Vector<Object>> getFraudAttempts(Connection conn) {
        List<Vector<Object>> fraudAttempts = new ArrayList<>();
        String sql = "SELECT fa.id, fa.voter_id_number, v.NAME, v.SURNAME, fa.attempt_type, "
                + "fa.timestamp, fa.details, fa.resolved, fa.attempt_count "
                + "FROM FraudAttempts fa "
                + "LEFT JOIN VOTERS v ON fa.voter_id_number = v.ID_NUMBER "
                + "ORDER BY fa.timestamp DESC LIMIT 50";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("voter_id_number"));
                row.add(rs.getString("NAME") + " " + rs.getString("SURNAME"));
                row.add(rs.getString("attempt_type"));
                row.add(rs.getTimestamp("timestamp"));
                row.add(rs.getString("details"));
                row.add(rs.getBoolean("resolved"));
                row.add(rs.getInt("attempt_count"));
                fraudAttempts.add(row);
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving fraud attempts: " + e.getMessage());
        }
        return fraudAttempts;
    }

    public static boolean resolveFraudAttempt(Connection conn, int fraudId) {
        String sql = "UPDATE FraudAttempts SET resolved = TRUE WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fraudId);
            boolean result = stmt.executeUpdate() > 0;
            if (result) {
                JOptionPane.showMessageDialog(null, "Fraud attempt marked as resolved!");
            }
            return result;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error resolving fraud attempt: " + e.getMessage());
            return false;
        }
    }

    // Get voting statistics summary
    public static Vector<Object> getVotingStatisticsSummary(Connection conn) {
        Vector<Object> summary = new Vector<>();
        try {
            // Total registered voters
            String totalVotersSql = "SELECT COUNT(*) as total FROM VOTERS";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(totalVotersSql)) {
                if (rs.next()) {
                    summary.add(rs.getInt("total"));
                }
            }

            // Total votes - Count unique voters instead of total vote rows
            String totalVotesSql = "SELECT COUNT(DISTINCT voter_id_number) as total FROM Votes";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(totalVotesSql)) {
                if (rs.next()) {
                    summary.add(rs.getInt("total"));
                }
            }

            // Votes today - Count unique voters who voted today
            String todayVotesSql = "SELECT COUNT(DISTINCT voter_id_number) as total FROM Votes WHERE DATE(vote_timestamp) = CURDATE()";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(todayVotesSql)) {
                if (rs.next()) {
                    summary.add(rs.getInt("total"));
                }
            }

            // Total casted ballots (actual rows in Votes table)
            String totalBallotsSql = "SELECT COUNT(*) as total FROM Votes";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(totalBallotsSql)) {
                if (rs.next()) {
                    summary.add(rs.getInt("total"));
                }
            }

            // Fraud attempts count
            String fraudSql = "SELECT COUNT(*) as total FROM FraudAttempts WHERE resolved = FALSE";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(fraudSql)) {
                if (rs.next()) {
                    summary.add(rs.getInt("total"));
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Error retrieving voting statistics: " + e.getMessage());
        }
        return summary;
    }
}