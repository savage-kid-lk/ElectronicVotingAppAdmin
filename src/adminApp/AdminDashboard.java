package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.util.List;
import java.util.Vector;

public class AdminDashboard extends JFrame {

    private JPanel mainPanel;
    private CardLayout cardLayout;

    private JTable candidateTable;
    private DefaultTableModel candidateModel;
    private String currentTable = "NationalBallot";
    private JPanel categoryTabs;

    private JTable voterTable;
    private DefaultTableModel voterModel;

    private JTable statsTable;
    private DefaultTableModel statsModel;

    private Connection conn;
    private JLabel header;

    public AdminDashboard(Connection connection) {
        this.conn = connection;

        setTitle("🗳 Voting Machine Admin Dashboard");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(0, 87, 183));
        setLayout(new BorderLayout());

        header = new JLabel("Voting Machine Admin Dashboard", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 26));
        header.setForeground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        add(header, BorderLayout.NORTH);

        JPanel navPanel = new JPanel();
        navPanel.setBackground(new Color(0, 87, 183));
        navPanel.setLayout(new GridLayout(2, 1, 0, 20));
        navPanel.setPreferredSize(new Dimension(250, 0));

        JButton manageVotersBtn = createNavButton("👥 Manage Voters");
        JButton manageCandidatesBtn = createNavButton("🏛 Manage Candidates");

        navPanel.add(manageVotersBtn);
        navPanel.add(manageCandidatesBtn);
        add(navPanel, BorderLayout.WEST);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        JPanel candidatePanel = createCandidatePanel();
        JPanel voterPanel = createVoterPanel();
        JPanel statsPanel = createStatsPanel();

        mainPanel.add(voterPanel, "VOTERS");
        mainPanel.add(candidatePanel, "CANDIDATES");
        mainPanel.add(statsPanel, "STATS");

        add(mainPanel, BorderLayout.CENTER);

        manageVotersBtn.addActionListener(e -> {
            loadVoters();
            cardLayout.show(mainPanel, "VOTERS");
        });
        manageCandidatesBtn.addActionListener(e -> {
            loadCandidates();
            cardLayout.show(mainPanel, "CANDIDATES");
        });

        loadCandidates();
        cardLayout.show(mainPanel, "CANDIDATES");
    }

    public void setAdminInfo(String name, String surname) {
        header.setText("Voting Machine Admin Dashboard — Logged in as: " + name + " " + surname);
    }

    private JButton createNavButton(String title) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 20));
        btn.setBackground(new Color(255, 209, 0));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel createCandidatePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        categoryTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        addCategoryTab("NationalBallot");
        addCategoryTab("RegionalBallot");
        addCategoryTab("ProvincialBallot");
        addStatisticsTab();
        panel.add(categoryTabs, BorderLayout.NORTH);

        String[] columns = {"Party", "Candidate", "Number of Votes"};
        candidateModel = new DefaultTableModel(columns, 0);
        candidateTable = new JTable(candidateModel);
        candidateTable.setRowHeight(30);
        panel.add(new JScrollPane(candidateTable), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton addBtn = new JButton("➕ Add Candidate");
        JButton deleteBtn = new JButton("🗑 Delete Candidate");
        JButton refreshBtn = new JButton("🔄 Refresh List");

        addBtn.addActionListener(e -> addCandidate());
        deleteBtn.addActionListener(e -> deleteCandidate());
        refreshBtn.addActionListener(e -> loadCandidates());

        buttonPanel.add(addBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void addCategoryTab(String tableName) {
        JButton tab = new JButton(tableName.replace("Ballot", ""));
        tab.setFocusPainted(false);
        tab.setBackground(Color.LIGHT_GRAY);
        tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        tab.addActionListener(e -> {
            currentTable = tableName;
            highlightActiveTab(tab);
            loadCandidates();
        });

        categoryTabs.add(tab);
        if (categoryTabs.getComponentCount() == 1) {
            highlightActiveTab(tab);
        }
    }

    private void addStatisticsTab() {
        JButton tab = new JButton("Statistics");
        tab.setFocusPainted(false);
        tab.setBackground(Color.LIGHT_GRAY);
        tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tab.addActionListener(e -> {
            loadStats();
            cardLayout.show(mainPanel, "STATS");
        });
        categoryTabs.add(tab);
    }

    private void highlightActiveTab(JButton activeTab) {
        for (Component comp : categoryTabs.getComponents()) {
            if (comp instanceof JButton) {
                comp.setBackground(comp == activeTab ? Color.YELLOW : Color.LIGHT_GRAY);
            }
        }
    }

    private void loadCandidates() {
        candidateModel.setRowCount(0);
        if (conn != null) {
            List<Vector<Object>> candidates = AdminDatabaseLogic.getAllCandidatesFromTable(conn, currentTable);
            for (Vector<Object> row : candidates) {
                candidateModel.addRow(row);
            }
        }
    }

    private void addCandidate() {
        // Radio buttons for candidate type
        JRadioButton partySupportedBtn = new JRadioButton("Party Supported");
        JRadioButton independentBtn = new JRadioButton("Independent");
        ButtonGroup candidateTypeGroup = new ButtonGroup();
        candidateTypeGroup.add(partySupportedBtn);
        candidateTypeGroup.add(independentBtn);
        partySupportedBtn.setSelected(true);

        // Input fields
        JTextField partyField = new JTextField();
        JTextField candidateNameField = new JTextField();

        // Dropdowns for regions and provinces
        String[] regions = {
            "Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape",
            "Free State", "Limpopo", "Mpumalanga", "North West", "Northern Cape"
        };
        String[] provinces = {
            "Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape",
            "Free State", "Limpopo", "Mpumalanga", "North West", "Northern Cape"
        };

        JComboBox<String> regionDropdown = new JComboBox<>(regions);
        JComboBox<String> provinceDropdown = new JComboBox<>(provinces);

        // Ballot checkboxes
        JCheckBox nationalBox = new JCheckBox("National");
        JCheckBox regionalBox = new JCheckBox("Regional");
        JCheckBox provincialBox = new JCheckBox("Provincial");

        // Image selection
        JButton partyLogoButton = new JButton("Select Party Logo");
        JButton candidateFaceButton = new JButton("Select Candidate Face");
        JLabel partyLogoLabel = new JLabel("No image selected");
        JLabel candidateFaceLabel = new JLabel("No image selected");
        final byte[][] partyLogoData = new byte[1][1];
        final byte[][] candidateFaceData = new byte[1][1];

        partyLogoButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.io.File file = chooser.getSelectedFile();
                    partyLogoData[0] = java.nio.file.Files.readAllBytes(file.toPath());
                    partyLogoLabel.setText(file.getName() + " selected");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "❌ Failed to read party logo: " + ex.getMessage());
                }
            }
        });

        candidateFaceButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.io.File file = chooser.getSelectedFile();
                    candidateFaceData[0] = java.nio.file.Files.readAllBytes(file.toPath());
                    candidateFaceLabel.setText(file.getName() + " selected");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "❌ Failed to read candidate image: " + ex.getMessage());
                }
            }
        });

        // Panel layout
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(partySupportedBtn);
        panel.add(independentBtn);

        panel.add(new JLabel("Party Abbreviation/Independent Name:"));
        panel.add(partyField);

        panel.add(new JLabel("Candidate Name:"));
        panel.add(candidateNameField);

        panel.add(new JLabel("Select Ballots:"));
        panel.add(nationalBox);
        panel.add(regionalBox);
        panel.add(provincialBox);

        // Region/Province dropdowns
        panel.add(new JLabel("Region:"));
        panel.add(regionDropdown);
        panel.add(new JLabel("Province:"));
        panel.add(provinceDropdown);

        // Image selectors
        panel.add(partyLogoButton);
        panel.add(partyLogoLabel);
        panel.add(candidateFaceButton);
        panel.add(candidateFaceLabel);

        // Dynamic visibility logic
        ActionListener toggleFields = e -> {
            boolean isParty = partySupportedBtn.isSelected();
            partyField.setVisible(isParty);
            partyLogoButton.setVisible(isParty);
            partyLogoLabel.setVisible(isParty);

            candidateFaceButton.setVisible(true);
            candidateFaceLabel.setVisible(true);

            regionDropdown.setVisible(regionalBox.isSelected());
            provinceDropdown.setVisible(provincialBox.isSelected());

            panel.revalidate();
            panel.repaint();
        };

        partySupportedBtn.addActionListener(toggleFields);
        independentBtn.addActionListener(toggleFields);
        regionalBox.addActionListener(toggleFields);
        provincialBox.addActionListener(toggleFields);

        // Initialize visibility
        toggleFields.actionPerformed(null);

        // Show dialog
        int result = JOptionPane.showConfirmDialog(this, panel, "Add Candidate",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            if (candidateFaceData[0] == null || candidateFaceData[0].length == 0) {
                JOptionPane.showMessageDialog(this, "⚠️ You must select a candidate image.");
                return;
            }

            String partyName = partySupportedBtn.isSelected() ? partyField.getText().trim() : "";
            String candidateName = candidateNameField.getText().trim();

            String value = "";
            if (regionalBox.isSelected()) {
                value = regionDropdown.getSelectedItem().toString();
            } else if (provincialBox.isSelected()) {
                value = provinceDropdown.getSelectedItem().toString();
            }

            // Fix: call updated method with 10 parameters
            boolean added = AdminDatabaseLogic.addCandidateToBallots(
                    conn,
                    partyName,
                    candidateName,
                    nationalBox.isSelected(),
                    regionalBox.isSelected(),
                    provincialBox.isSelected(),
                    candidateFaceData[0], // candidate image
                    partyLogoData[0], // party logo (null if independent)
                    !partySupportedBtn.isSelected(), // isIndependent
                    value
            );

            if (added) {
                loadCandidates();
            } else {
                JOptionPane.showMessageDialog(this, "❌ Failed to add candidate");
            }
        }
    }

    private void deleteCandidate() {
        int selectedRow = candidateTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "⚠️ Please select a candidate to delete.");
            return;
        }

        String partyName = (String) candidateModel.getValueAt(selectedRow, 0);
        String candidateName = (String) candidateModel.getValueAt(selectedRow, 1);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete candidate \"" + candidateName + "\" and all their votes?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            boolean deleted = AdminDatabaseLogic.deleteCandidateFromTable(conn, currentTable, partyName, candidateName);
            if (deleted) {
                JOptionPane.showMessageDialog(this, "✅ Candidate and related votes deleted successfully.");
                loadCandidates();
            } else {
                JOptionPane.showMessageDialog(this, "❌ Failed to delete candidate or votes.");
            }
        }
    }

    private JPanel createVoterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columns = {"Name", "Surname", "ID Number"};
        voterModel = new DefaultTableModel(columns, 0);
        voterTable = new JTable(voterModel);
        voterTable.setRowHeight(26);
        voterTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(new JScrollPane(voterTable), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton addBtn = new JButton("➕ Add Voter");
        JButton deleteBtn = new JButton("🗑 Delete Voter");
        JButton refreshBtn = new JButton("🔄 Refresh List");

        addBtn.addActionListener(e -> {
            try {
                ReaderCollection collection = UareUGlobal.GetReaderCollection();
                collection.GetReaders();
                Reader reader = collection.size() > 0 ? collection.get(0) : null;

                if (reader == null) {
                    JOptionPane.showMessageDialog(this, "❌ No fingerprint reader found!");
                    return;
                }

                AddVoters.Run(reader, conn);
                loadVoters();

                UareUGlobal.DestroyReaderCollection();
            } catch (UareUException ex) {
                ex.printStackTrace();
            }
        });

        deleteBtn.addActionListener(e -> deleteVoter());
        refreshBtn.addActionListener(e -> loadVoters());

        buttonPanel.add(addBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columns = {"Party", "Total Votes", "Votes Today"};
        statsModel = new DefaultTableModel(columns, 0);
        statsTable = new JTable(statsModel);
        statsTable.setRowHeight(28);
        panel.add(new JScrollPane(statsTable), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("🔄 Refresh Stats");
        refreshBtn.addActionListener(e -> loadStats());
        JPanel southPanel = new JPanel();
        southPanel.add(refreshBtn);
        panel.add(southPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadStats() {
        statsModel.setRowCount(0);
        if (conn != null) {
            List<Vector<Object>> stats = AdminDatabaseLogic.getVoteStatistics(conn);
            for (Vector<Object> row : stats) {
                statsModel.addRow(row);
            }
        }
    }

    private void loadVoters() {
        voterModel.setRowCount(0);
        if (conn != null) {
            List<Vector<Object>> voters = AdminDatabaseLogic.getAllVoters(conn);
            for (Vector<Object> row : voters) {
                voterModel.addRow(row);
            }
        }
    }

    private void deleteVoter() {
        int selectedRow = voterTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "⚠️ Please select a voter to delete.");
            return;
        }
        String idNumber = (String) voterModel.getValueAt(selectedRow, 2);
        if (AdminDatabaseLogic.deleteVoter(conn, idNumber)) {
            loadVoters();
        }
    }
}
