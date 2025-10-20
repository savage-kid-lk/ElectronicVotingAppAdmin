package adminApp;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
            LocalDate date = LocalDate.parse(birth, formatter);
            if (date.isAfter(LocalDate.now())) {
                return false;
            }
        } catch (DateTimeParseException e) {
            return false;
        }
        char citizenship = id.charAt(10);
        if (citizenship != '0' && citizenship != '1') {
            return false;
        }
        return luhnCheck(id);
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

    public static void saveVoter(Connection conn, byte[] fidData, String name, String surname, String idNum) {
        if (!allFieldsFilled(name, surname, idNum)) {
            return;
        }
        if (!isValidSouthAfricanID(idNum)) {
            return;
        }
        try {
            if (idExists(conn, "VOTERS", idNum)) {
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Add this voter?\nName: " + name + " " + surname + "\nID: " + idNum,
                    "Confirm Add Voter", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            String sql = "INSERT INTO VOTERS (FINGERPRINT, NAME, SURNAME, ID_NUMBER) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBytes(1, fidData);
                stmt.setString(2, name);
                stmt.setString(3, surname);
                stmt.setString(4, idNum);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static void saveAdmin(Connection conn, byte[] fidData, String name, String surname, String idNum) {
        if (!allFieldsFilled(name, surname, idNum)) {
            return;
        }
        if (!isValidSouthAfricanID(idNum)) {
            return;
        }
        try {
            if (idExists(conn, "Admins", idNum)) {
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Add this admin?\nName: " + name + " " + surname + "\nID: " + idNum,
                    "Confirm Add Admin", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            String sql = "INSERT INTO Admins (FINGERPRINT, NAME, SURNAME, ID_NUMBER) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBytes(1, fidData);
                stmt.setString(2, name);
                stmt.setString(3, surname);
                stmt.setString(4, idNum);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static List<Vector<Object>> getAllVoters(Connection conn) {
        List<Vector<Object>> voters = new ArrayList<>();
        String sql = "SELECT NAME, SURNAME, ID_NUMBER, FINGERPRINT FROM VOTERS";
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
            System.err.println(e.getMessage());
        }
        return voters;
    }

    public static boolean deleteVoter(Connection conn, String idNumber) {
        String sql = "DELETE FROM VOTERS WHERE ID_NUMBER = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, idNumber);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    public static boolean addCandidateToBallots(Connection conn, String partyName, String candidateName,
            boolean national, boolean regional, boolean provincial, byte[] candidateImage,
            byte[] partyLogo, boolean isIndependent, String value) {

        boolean success = false;

        // If independent, override party name to "Independent" and ignore party logo
        if (isIndependent) {
            partyName = "Independent";
            partyLogo = null;
        }

        try {
            if (national) {
                // National now accepts candidate_image + optional party_logo
                success |= addCandidateToTable(conn, "NationalBallot", partyName, candidateName, candidateImage, partyLogo, null);
            }
            if (regional) {
                success |= addCandidateToTable(conn, "RegionalBallot", partyName, candidateName, candidateImage, partyLogo, value);
            }
            if (provincial) {
                success |= addCandidateToTable(conn, "ProvincialBallot", partyName, candidateName, candidateImage, partyLogo, value);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
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
        } else { // NationalBallot
            sql = "INSERT INTO " + tableName + " (party_name, candidate_name, candidate_image, party_logo) VALUES (?, ?, ?, ?)";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, partyName);
            stmt.setString(2, candidateName);
            stmt.setBytes(3, candidateImage);

            if (tableName.equals("NationalBallot")) {
                // Party logo is optional for national; if independent, it will be null
                stmt.setBytes(4, partyLogo);
            } else {
                stmt.setBytes(4, partyLogo); // if independent, partyLogo is null
                stmt.setString(5, value);    // region or province
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
                + "LEFT JOIN (SELECT party_name, COUNT(*) AS vote_count FROM Vote WHERE category = ? GROUP BY party_name) v "
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

        // Determine the category based on table name
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
                System.err.println("❌ Unknown table name: " + tableName);
                return false;
        }

        try {
            conn.setAutoCommit(false); // start transaction

            // Step 1: Delete candidate from the ballot table
            String deleteCandidateQuery = "DELETE FROM " + tableName + " WHERE candidate_name = ? AND party_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteCandidateQuery)) {
                ps.setString(1, candidateName);
                ps.setString(2, partyName);
                ps.executeUpdate();
            }

            // Step 2: Delete votes linked to this candidate in the Vote table
            String deleteVotesQuery = "DELETE FROM Vote WHERE party_name = ? AND category = ?";
            try (PreparedStatement psVotes = conn.prepareStatement(deleteVotesQuery)) {
                psVotes.setString(1, partyName);
                psVotes.setString(2, category);
                psVotes.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
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
        String sql = "SELECT c.party_name, COALESCE(v.total_votes, 0) AS total_votes, "
                + "COALESCE(v.today_votes, 0) AS votes_today FROM "
                + "(SELECT DISTINCT party_name FROM Vote "
                + "UNION SELECT DISTINCT party_name FROM NationalBallot "
                + "UNION SELECT DISTINCT party_name FROM RegionalBallot "
                + "UNION SELECT DISTINCT party_name FROM ProvincialBallot) c "
                + "LEFT JOIN (SELECT party_name, COUNT(*) AS total_votes, "
                + "SUM(CASE WHEN DATE(timestamp) = CURDATE() THEN 1 ELSE 0 END) AS today_votes "
                + "FROM Vote GROUP BY party_name) v ON c.party_name = v.party_name "
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
            System.err.println(e.getMessage());
        }
        return stats;
    }
}
