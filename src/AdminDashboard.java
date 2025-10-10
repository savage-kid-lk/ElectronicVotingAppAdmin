

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;

import com.digitalpersona.uareu.*;

/**
 * Admin dashboard for managing voters. Works with Apache Derby database and
 * DigitalPersona fingerprint SDK.
 */
public class AdminDashboard extends JFrame {

    private JTabbedPane tabbedPane;
    private JTable voterTable;
    private DefaultTableModel voterModel;
    private Reader reader;

    public AdminDashboard() {
        setTitle("🗳 Voting Machine Admin Dashboard");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(0, 87, 183));
        setLayout(new BorderLayout());

        // HEADER
        JLabel header = new JLabel("Voting Machine Admin Dashboard", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 26));
        header.setForeground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        add(header, BorderLayout.NORTH);

        // MAIN CONTENT
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabbedPane.setBackground(Color.WHITE);

        tabbedPane.addTab("👥 Candidates", placeholderTab("👥 Candidate Management Coming Soon"));
        tabbedPane.addTab("🗳 Voters", createVoterPanel());
        tabbedPane.addTab("📊 Results", placeholderTab("📊 Results Module Coming Soon"));
        tabbedPane.addTab("⚙ Settings", placeholderTab("⚙ System Settings Coming Soon"));

        add(tabbedPane, BorderLayout.CENTER);

        // FOOTER
        JButton logoutBtn = new JButton("🚪 Logout");
        styleButton(logoutBtn);
        logoutBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Logging out...");
            dispose();
        });

        JPanel footer = new JPanel();
        footer.setBackground(new Color(0, 87, 183));
        footer.add(logoutBtn);
        add(footer, BorderLayout.SOUTH);

        // Initialize Fingerprint Reader
        try {
            ReaderCollection readers = UareUGlobal.GetReaderCollection();
            readers.GetReaders();
            if (readers.size() > 0) {
                reader = readers.get(0);
                System.out.println("✅ Using fingerprint reader: " + reader.GetDescription().name);
            } else {
                JOptionPane.showMessageDialog(this, "⚠️ No fingerprint readers detected.", "Warning", JOptionPane.WARNING_MESSAGE);
            }
        } catch (UareUException e) {
            JOptionPane.showMessageDialog(this, "Error initializing fingerprint reader: " + e.getMessage());
        }

        // Load voters on startup
        loadVoters();
    }

    /**
     * ---------- STYLING ----------
     */
    private void styleButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setBackground(new Color(255, 209, 0));
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(180, 40));
    }

    /**
     * ---------- PLACEHOLDER TABS ----------
     */
    private JPanel placeholderTab(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        JLabel label = new JLabel("<html><center><h2>" + text + "</h2></center></html>", SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    /**
     * ---------- VOTER TAB ----------
     */
    private JPanel createVoterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        String[] columns = {"Name", "Surname", "ID Number", "Fingerprint"};
        voterModel = new DefaultTableModel(columns, 0);
        voterTable = new JTable(voterModel);
        voterTable.setRowHeight(26);
        voterTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JScrollPane scrollPane = new JScrollPane(voterTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.WHITE);

        JButton addBtn = new JButton("➕ Add Voter");
        JButton deleteBtn = new JButton("🗑 Delete Voter");
        JButton refreshBtn = new JButton("🔄 Refresh List");

        styleButton(addBtn);
        styleButton(deleteBtn);
        styleButton(refreshBtn);

        buttonPanel.add(addBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Button actions
        addBtn.addActionListener(e -> {
            EnrollmentGUI.Run(reader);
            loadVoters();
        });

        deleteBtn.addActionListener(e -> deleteVoter());
        refreshBtn.addActionListener(e -> loadVoters());

        return panel;
    }

    private void loadVoters() {
        voterModel.setRowCount(0);
        try (ResultSet rs = FingerprintDAO.getAllVoters()) {

            while (rs != null && rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getString("name"));
                row.add(rs.getString("surname"));
                row.add(rs.getString("id_number"));
                row.add(rs.getBytes("fingerprint") != null ? "✅" : "");
                voterModel.addRow(row);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "❌ Error loading voters: " + e.getMessage());
        }
    }

    /**
     * ---------- DELETE VOTER ----------
     */
    private void deleteVoter() {
        int selectedRow = voterTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "⚠️ Please select a voter to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get ID number from table
        String idNumber = (String) voterModel.getValueAt(selectedRow, 2);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete this voter?\nID: " + idNumber,
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            boolean success = FingerprintDAO.deleteVoter(idNumber);

            if (success) {
                JOptionPane.showMessageDialog(this, "✅ Voter deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadVoters(); // Refresh table
            } else {
                JOptionPane.showMessageDialog(this, "⚠️ No voter found with that ID.", "Not Found", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /**
     * ---------- MAIN ----------
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminDashboard().setVisible(true));
    }
}
