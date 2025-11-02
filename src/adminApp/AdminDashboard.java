package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

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

    private JTable fraudTable;
    private DefaultTableModel fraudModel;

    private Connection conn;
    private JLabel header;
    private JTextArea fraudReportsArea;
    private AdminLogin loginWindow;
    private Timer connectionMonitorTimer;
    
    // Search fields
    private JTextField candidateSearchField;
    private JTextField voterSearchField;
    private JTextField statsSearchField;

    public AdminDashboard(Connection connection, AdminLogin loginWindow) {
        this.conn = connection;
        this.loginWindow = loginWindow;
        initializeUI();
        startConnectionMonitoring();
    }

    /**
     * Start monitoring connection status
     */
    private void startConnectionMonitoring() {
        connectionMonitorTimer = new Timer(true);
        connectionMonitorTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkConnectionStatus();
            }
        }, 30000, 30000); // Check every 30 seconds
    }

    /**
     * Check if connection is still valid
     */
    private void checkConnectionStatus() {
        if (AdminDatabaseConnectivity.shouldRedirectToLogin()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(AdminDashboard.this,
                    "❌ Database connection lost. You will be redirected to login.",
                    "Connection Timeout",
                    JOptionPane.WARNING_MESSAGE);
                redirectToLogin();
            });
        }
    }

    /**
     * Redirect back to login window
     */
    private void redirectToLogin() {
        if (connectionMonitorTimer != null) {
            connectionMonitorTimer.cancel();
        }
        
        // Close current window
        dispose();
        
        // Show login window again
        if (loginWindow != null) {
            loginWindow.showLoginAgain();
        } else {
            // Fallback: create new login window
            new AdminLogin().setVisible(true);
        }
    }

    /**
     * Enhanced database operations with connection checking
     */
    private boolean checkConnectionBeforeOperation() {
        if (AdminDatabaseConnectivity.shouldRedirectToLogin()) {
            JOptionPane.showMessageDialog(this,
                "❌ Database connection lost. Please login again.",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE);
            redirectToLogin();
            return false;
        }
        return true;
    }

    /**
     * Handle database errors and redirect if connection is lost
     */
    private void handleDatabaseError(Exception e) {
        System.err.println("Database error: " + e.getMessage());
        
        // Check if this is a connection timeout error
        if (e.getMessage().contains("connection") || e.getMessage().contains("timeout") || 
            e.getMessage().contains("closed")) {
            AdminDatabaseConnectivity.setConnectionLost(true);
            redirectToLogin();
        } else {
            JOptionPane.showMessageDialog(this,
                "❌ Database error: " + e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initializeUI() {
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

        // Create main content panel with left nav, center content, and right fraud panel
        JPanel contentPanel = new JPanel(new BorderLayout());
        
        // Left navigation panel
        JPanel navPanel = new JPanel();
        navPanel.setBackground(new Color(0, 87, 183));
        navPanel.setLayout(new GridLayout(3, 1, 0, 10));
        navPanel.setPreferredSize(new Dimension(250, 0));

        JButton manageVotersBtn = createNavButton("👥 Manage Voters");
        JButton manageCandidatesBtn = createNavButton("🏛 Manage Candidates");
        JButton viewStatisticsBtn = createNavButton("📊 View Statistics");

        navPanel.add(manageVotersBtn);
        navPanel.add(manageCandidatesBtn);
        navPanel.add(viewStatisticsBtn);
        contentPanel.add(navPanel, BorderLayout.WEST);

        // Center main panel with card layout
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        JPanel candidatePanel = createCandidatePanel();
        JPanel voterPanel = createVoterPanel();
        JPanel statsPanel = createStatsPanel();
        JPanel fraudPanel = createFraudPanel();

        mainPanel.add(voterPanel, "VOTERS");
        mainPanel.add(candidatePanel, "CANDIDATES");
        mainPanel.add(statsPanel, "STATS");
        mainPanel.add(fraudPanel, "FRAUD");

        contentPanel.add(mainPanel, BorderLayout.CENTER);

        // Right fraud reports panel
        JPanel fraudSidePanel = createFraudReportsPanel();
        fraudSidePanel.setPreferredSize(new Dimension(250, 0));
        contentPanel.add(fraudSidePanel, BorderLayout.EAST);

        add(contentPanel, BorderLayout.CENTER);

        manageVotersBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            loadVoters();
            cardLayout.show(mainPanel, "VOTERS");
        });
        manageCandidatesBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            loadCandidates();
            cardLayout.show(mainPanel, "CANDIDATES");
        });
        viewStatisticsBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            loadStats();
            cardLayout.show(mainPanel, "STATS");
        });

        loadCandidates();
        cardLayout.show(mainPanel, "CANDIDATES");
    }

    private JPanel createFraudPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        String[] columns = {"ID", "Voter ID", "Voter Name", "Type", "Timestamp", "Details", "Resolved", "Attempts"};
        fraudModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6; // Only resolved column is editable
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 6) return Boolean.class;
                return String.class;
            }
        };
        
        fraudTable = new JTable(fraudModel);
        fraudTable.setRowHeight(25);
        fraudTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        fraudTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fraudTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        fraudTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        fraudTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        fraudTable.getColumnModel().getColumn(5).setPreferredWidth(200);
        fraudTable.getColumnModel().getColumn(6).setPreferredWidth(80);
        fraudTable.getColumnModel().getColumn(7).setPreferredWidth(80);
        
        // Listen for resolution changes
        fraudModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 6) {
                int row = e.getFirstRow();
                int fraudId = (int) fraudModel.getValueAt(row, 0);
                boolean resolved = (boolean) fraudModel.getValueAt(row, 6);
                
                if (resolved) {
                    if (!checkConnectionBeforeOperation()) return;
                    AdminDatabaseLogic.resolveFraudAttempt(conn, fraudId);
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(fraudTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel();
        JButton refreshBtn = new JButton("🔄 Refresh Fraud Data");
        refreshBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            loadFraudData();
        });
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    

    private void loadFraudData() {
        if (!checkConnectionBeforeOperation()) return;
        
        fraudModel.setRowCount(0);
        if (conn != null) {
            try {
                List<Vector<Object>> fraudAttempts = AdminDatabaseLogic.getFraudAttempts(conn);
                for (Vector<Object> row : fraudAttempts) {
                    fraudModel.addRow(row);
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private JPanel createFraudReportsPanel() {
        JPanel fraudPanel = new JPanel(new BorderLayout());
        fraudPanel.setBackground(new Color(0, 87, 183));
        fraudPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header
        JLabel fraudHeader = new JLabel("Fraud Reports", SwingConstants.CENTER);
        fraudHeader.setFont(new Font("Segoe UI", Font.BOLD, 18));
        fraudHeader.setForeground(Color.WHITE);
        fraudHeader.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        fraudPanel.add(fraudHeader, BorderLayout.NORTH);

        // Scrollable fraud reports area
        fraudReportsArea = new JTextArea();
        fraudReportsArea.setEditable(false);
        fraudReportsArea.setBackground(new Color(240, 240, 240));
        fraudReportsArea.setForeground(Color.BLACK);
        fraudReportsArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fraudReportsArea.setLineWrap(true);
        fraudReportsArea.setWrapStyleWord(true);

        JScrollPane fraudScrollPane = new JScrollPane(fraudReportsArea);
        fraudScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY), 
            "Recent Activity"
        ));
        fraudScrollPane.setPreferredSize(new Dimension(230, 400));
        fraudPanel.add(fraudScrollPane, BorderLayout.CENTER);

        // Refresh button for fraud reports
        JButton refreshFraudBtn = new JButton("🔄 Refresh Reports");
        refreshFraudBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refreshFraudBtn.setBackground(new Color(255, 209, 0));
        refreshFraudBtn.setForeground(Color.BLACK);
        refreshFraudBtn.setFocusPainted(false);
        refreshFraudBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshFraudBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            refreshFraudReports();
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(0, 87, 183));
        buttonPanel.add(refreshFraudBtn);
        fraudPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Load initial fraud reports
        refreshFraudReports();

        return fraudPanel;
    }

    private void refreshFraudReports() {
        if (!checkConnectionBeforeOperation()) return;
        
        StringBuilder reports = new StringBuilder();
        reports.append("=== Real-time Fraud Detection ===\n");
        reports.append("Last Updated: ").append(new java.util.Date()).append("\n\n");
        
        if (conn != null) {
            try {
                Vector<Object> stats = AdminDatabaseLogic.getVotingStatisticsSummary(conn);
                if (stats.size() >= 4) {
                    reports.append("📊 VOTING STATISTICS:\n");
                    reports.append("• Total Voters: ").append(stats.get(0)).append("\n");
                    reports.append("• Total Votes: ").append(stats.get(1)).append("\n");
                    reports.append("• Votes Today: ").append(stats.get(2)).append("\n");
                    reports.append("• Active Fraud Cases: ").append(stats.get(3)).append("\n\n");
                }

                List<Vector<Object>> recentFraud = AdminDatabaseLogic.getFraudAttempts(conn);
                if (!recentFraud.isEmpty()) {
                    reports.append("🚨 RECENT FRAUD ATTEMPTS:\n");
                    int count = 0;
                    for (Vector<Object> fraud : recentFraud) {
                        if (count >= 5) break; // Show only 5 most recent
                        boolean resolved = (boolean) fraud.get(6);
                        if (!resolved) {
                            reports.append("• ").append(fraud.get(3)).append(" - ")
                                  .append(fraud.get(2)).append(" (ID: ").append(fraud.get(1)).append(")\n");
                            count++;
                        }
                    }
                    if (count == 0) {
                        reports.append("• No active fraud cases\n");
                    }
                } else {
                    reports.append("✅ No fraud attempts detected.\n");
                }
            } catch (Exception e) {
                handleDatabaseError(e);
                reports.append("❌ Error loading fraud data: ").append(e.getMessage()).append("\n");
            }
        } else {
            reports.append("❌ Database connection unavailable\n");
        }

        fraudReportsArea.setText(reports.toString());
    }

    public void setAdminInfo(String name, String surname) {
        header.setText("Voting Machine Admin Dashboard — Logged in as: " + name + " " + surname);
    }

    private JButton createNavButton(String title) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setBackground(new Color(255, 209, 0));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel createCandidatePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // ✅ ENHANCED: Search panel for candidates
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        searchPanel.setBackground(Color.WHITE);
        
        JLabel searchLabel = new JLabel("🔍 Search Candidates:");
        candidateSearchField = new JTextField(25);
        candidateSearchField.setToolTipText("Search by party name, candidate name, region, or province. Examples: 'EFF', 'Cyril Ramaphosa', 'Gauteng'");
        
        JButton searchBtn = new JButton("Search");
        JButton clearSearchBtn = new JButton("Clear");
        JButton allCandidatesBtn = new JButton("Show All Ballots");
        
        searchBtn.addActionListener(e -> searchCandidates());
        clearSearchBtn.addActionListener(e -> {
            candidateSearchField.setText("");
            loadCandidates();
        });
        allCandidatesBtn.addActionListener(e -> searchAllCandidates());
        
        JPanel searchButtonPanel = new JPanel();
        searchButtonPanel.add(searchBtn);
        searchButtonPanel.add(clearSearchBtn);
        searchButtonPanel.add(allCandidatesBtn);
        
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(candidateSearchField, BorderLayout.CENTER);
        searchPanel.add(searchButtonPanel, BorderLayout.EAST);
        
        panel.add(searchPanel, BorderLayout.NORTH);
        
        // Fixed category tabs - always visible
        categoryTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        addCategoryTab("NationalBallot");
        addCategoryTab("RegionalBallot");
        addCategoryTab("ProvincialBallot");
        panel.add(categoryTabs, BorderLayout.CENTER);

        String[] columns = {"Party", "Candidate", "Number of Votes"};
        candidateModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 1;
            }
        };
        candidateTable = new JTable(candidateModel);
        candidateTable.setRowHeight(30);
        panel.add(new JScrollPane(candidateTable), BorderLayout.CENTER);

        // Listen for updates and push to DB
        candidateModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && conn != null) {
                if (!checkConnectionBeforeOperation()) return;
                
                int row = e.getFirstRow();
                int column = e.getColumn();
                if (row >= 0 && (column == 0 || column == 1)) {
                    String newValue = candidateModel.getValueAt(row, column).toString();

                    // Determine old values
                    String oldParty = candidateModel.getValueAt(row, 0).toString();
                    String oldCandidate = candidateModel.getValueAt(row, 1).toString();

                    // Determine region/province for Regional/Provincial ballots
                    String regionOrProvince = "";
                    if (currentTable.equalsIgnoreCase("RegionalBallot")) {
                        // Assuming the 3rd column stores the region
                        regionOrProvince = candidateModel.getValueAt(row, 2).toString();
                    } else if (currentTable.equalsIgnoreCase("ProvincialBallot")) {
                        // Assuming the 3rd column stores the province
                        regionOrProvince = candidateModel.getValueAt(row, 2).toString();
                    }

                    try {
                        AdminDatabaseLogic.updateCandidate(conn, currentTable, column, newValue, oldParty, oldCandidate, regionOrProvince);
                    } catch (Exception ex) {
                        handleDatabaseError(ex);
                    }
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        JButton addBtn = new JButton("➕ Add Candidate");
        JButton deleteBtn = new JButton("🗑 Delete Candidate");
        JButton refreshBtn = new JButton("🔄 Refresh List");

        addBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            addCandidate();
        });
        deleteBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            deleteCandidate();
        });
        refreshBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            loadCandidates();
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ✅ NEW: Search candidates across all ballots
    private void searchAllCandidates() {
        String searchTerm = candidateSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            JOptionPane.showMessageDialog(this, "⚠️ Please enter a search term.");
            return;
        }

        if (!checkConnectionBeforeOperation()) return;
        
        candidateModel.setRowCount(0);
        if (conn != null) {
            try {
                List<Vector<Object>> candidates = AdminDatabaseLogic.searchCandidates(conn, searchTerm);
                if (candidates.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "❌ No candidates found matching: " + searchTerm);
                } else {
                    // Change columns to include ballot type
                    candidateModel.setColumnIdentifiers(new String[]{"Party", "Candidate", "Ballot Type", "Votes"});
                    for (Vector<Object> row : candidates) {
                        candidateModel.addRow(row);
                    }
                    JOptionPane.showMessageDialog(this, "✅ Found " + candidates.size() + " candidates across all ballots matching: " + searchTerm);
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    // ✅ ENHANCED: Search candidates in current ballot
    private void searchCandidates() {
        String searchTerm = candidateSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadCandidates();
            return;
        }

        if (!checkConnectionBeforeOperation()) return;
        
        candidateModel.setRowCount(0);
        if (conn != null) {
            try {
                // For single ballot search, we'll filter the existing data
                List<Vector<Object>> allCandidates = AdminDatabaseLogic.getAllCandidatesFromTable(conn, currentTable);
                List<Vector<Object>> filteredCandidates = new ArrayList<>();
                
                String lowerSearch = searchTerm.toLowerCase();
                for (Vector<Object> row : allCandidates) {
                    String party = row.get(0).toString().toLowerCase();
                    String candidate = row.get(1).toString().toLowerCase();
                    
                    if (party.contains(lowerSearch) || candidate.contains(lowerSearch) ||
                        (party + " " + candidate).contains(lowerSearch) ||
                        (candidate + " " + party).contains(lowerSearch)) {
                        filteredCandidates.add(row);
                    }
                }
                
                if (filteredCandidates.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "❌ No candidates found in " + currentTable.replace("Ballot", "") + " ballot matching: " + searchTerm);
                } else {
                    for (Vector<Object> row : filteredCandidates) {
                        candidateModel.addRow(row);
                    }
                    JOptionPane.showMessageDialog(this, "✅ Found " + filteredCandidates.size() + " candidates in " + currentTable.replace("Ballot", "") + " ballot matching: " + searchTerm);
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private void addCategoryTab(String tableName) {
        JButton tab = new JButton(tableName.replace("Ballot", ""));
        tab.setFocusPainted(false);
        tab.setBackground(Color.LIGHT_GRAY);
        tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        tab.addActionListener(e -> {
            currentTable = tableName;
            highlightActiveTab(tab);
            if (!checkConnectionBeforeOperation()) return;
            loadCandidates();
        });

        categoryTabs.add(tab);
        if (categoryTabs.getComponentCount() == 1) {
            highlightActiveTab(tab);
        }
    }

    private void highlightActiveTab(JButton activeTab) {
        for (Component comp : categoryTabs.getComponents()) {
            if (comp instanceof JButton) {
                comp.setBackground(comp == activeTab ? Color.YELLOW : Color.LIGHT_GRAY);
            }
        }
    }

    private void loadCandidates() {
        if (!checkConnectionBeforeOperation()) return;
        
        candidateModel.setRowCount(0);
        candidateModel.setColumnIdentifiers(new String[]{"Party", "Candidate", "Number of Votes"});
        
        if (conn != null) {
            try {
                List<Vector<Object>> candidates = AdminDatabaseLogic.getAllCandidatesFromTable(conn, currentTable);
                for (Vector<Object> row : candidates) {
                    candidateModel.addRow(row);
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private void addCandidate() {
        if (!checkConnectionBeforeOperation()) return;
        
        // Full, updated version with images, region, and ballot selection
        JRadioButton partySupportedBtn = new JRadioButton("Party Supported");
        JRadioButton independentBtn = new JRadioButton("Independent");
        ButtonGroup candidateTypeGroup = new ButtonGroup();
        candidateTypeGroup.add(partySupportedBtn);
        candidateTypeGroup.add(independentBtn);
        partySupportedBtn.setSelected(true);

        JTextField partyField = new JTextField();
        JTextField candidateNameField = new JTextField();

        String[] regions = {"Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape",
            "Free State", "Limpopo", "Mpumalanga", "North West", "Northern Cape"};
        String[] provinces = regions;

        JComboBox<String> regionDropdown = new JComboBox<>(regions);
        JComboBox<String> provinceDropdown = new JComboBox<>(provinces);

        JCheckBox nationalBox = new JCheckBox("National");
        JCheckBox regionalBox = new JCheckBox("Regional");
        JCheckBox provincialBox = new JCheckBox("Provincial");

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
                    JOptionPane.showMessageDialog(this, "Failed to read party logo: " + ex.getMessage());
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
                    JOptionPane.showMessageDialog(this, "Failed to read candidate image: " + ex.getMessage());
                }
            }
        });

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
        panel.add(new JLabel("Region:"));
        panel.add(regionDropdown);
        panel.add(new JLabel("Province:"));
        panel.add(provinceDropdown);
        panel.add(partyLogoButton);
        panel.add(partyLogoLabel);
        panel.add(candidateFaceButton);
        panel.add(candidateFaceLabel);

        ActionListener toggleFields = e -> {
            boolean isParty = partySupportedBtn.isSelected();
            partyField.setVisible(isParty);
            partyLogoButton.setVisible(isParty);
            partyLogoLabel.setVisible(isParty);

            regionDropdown.setVisible(regionalBox.isSelected());
            provinceDropdown.setVisible(provincialBox.isSelected());

            panel.revalidate();
            panel.repaint();
        };

        partySupportedBtn.addActionListener(toggleFields);
        independentBtn.addActionListener(toggleFields);
        regionalBox.addActionListener(toggleFields);
        provincialBox.addActionListener(toggleFields);
        toggleFields.actionPerformed(null);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Candidate", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            if (candidateFaceData[0] == null || candidateFaceData[0].length == 0) {
                JOptionPane.showMessageDialog(this, "You must select a candidate image.");
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

            try {
                boolean added = AdminDatabaseLogic.addCandidateToBallots(
                        conn,
                        partyName,
                        candidateName,
                        nationalBox.isSelected(),
                        regionalBox.isSelected(),
                        provincialBox.isSelected(),
                        candidateFaceData[0],
                        partyLogoData[0],
                        !partySupportedBtn.isSelected(),
                        value
                );

                if (added) {
                    loadCandidates();
                }
            } catch (Exception ex) {
                handleDatabaseError(ex);
            }
        }
    }

    private void deleteCandidate() {
        if (!checkConnectionBeforeOperation()) return;
        
        int selectedRow = candidateTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a candidate to delete.");
            return;
        }

        String partyName = (String) candidateModel.getValueAt(selectedRow, 0);
        String candidateName = (String) candidateModel.getValueAt(selectedRow, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete candidate \"" + candidateName + "\" and all their votes?",
                "Confirm Deletion", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                boolean deleted = AdminDatabaseLogic.deleteCandidateFromTable(conn, currentTable, partyName, candidateName);
                if (deleted) {
                    loadCandidates();
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private JPanel createVoterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // ✅ ENHANCED: Search panel for voters
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        searchPanel.setBackground(Color.WHITE);
        
        JLabel searchLabel = new JLabel("Search Voters:");
        voterSearchField = new JTextField(25);
        voterSearchField.setToolTipText("Search by name, surname, ID number, voting status. Examples: 'John', '990101', 'voted', 'not voted'");
        
        JButton searchBtn = new JButton("Search");
        JButton clearSearchBtn = new JButton("Clear");
        
        searchBtn.addActionListener(e -> searchVoters());
        clearSearchBtn.addActionListener(e -> {
            voterSearchField.setText("");
            loadVoters();
        });
        
        JPanel searchButtonPanel = new JPanel();
        searchButtonPanel.add(searchBtn);
        searchButtonPanel.add(clearSearchBtn);
        
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(voterSearchField, BorderLayout.CENTER);
        searchPanel.add(searchButtonPanel, BorderLayout.EAST);
        
        panel.add(searchPanel, BorderLayout.NORTH);

        String[] columns = {"Name", "Surname", "ID Number", "Fingerprint", "Has Voted"};
        voterModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 1;
            }
        };
        voterTable = new JTable(voterModel);
        voterTable.setRowHeight(26);
        voterTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(new JScrollPane(voterTable), BorderLayout.CENTER);

        voterModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && conn != null) {
                if (!checkConnectionBeforeOperation()) return;
                
                int row = e.getFirstRow();
                int column = e.getColumn();
                if (row >= 0 && (column == 0 || column == 1)) {
                    String idNumber = voterModel.getValueAt(row, 2).toString();
                    String newValue = voterModel.getValueAt(row, column).toString();
                    try {
                        AdminDatabaseLogic.updateVoter(conn, idNumber, column, newValue);
                    } catch (Exception ex) {
                        handleDatabaseError(ex);
                    }
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        JButton addBtn = new JButton("Add Voter");
        JButton deleteBtn = new JButton("Delete Voter");
        JButton refreshBtn = new JButton("Refresh");

        addBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            try {
                ReaderCollection collection = UareUGlobal.GetReaderCollection();
                collection.GetReaders();
                Reader reader = collection.size() > 0 ? collection.get(0) : null;
                if (reader == null) {
                    JOptionPane.showMessageDialog(this, "No fingerprint reader found!");
                    return;
                }
                AddVoters.Run(reader, conn);
                loadVoters();
                UareUGlobal.DestroyReaderCollection();
            } catch (UareUException ex) {
                ex.printStackTrace();
            } catch (Exception ex) {
                handleDatabaseError(ex);
            }
        });

        deleteBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            deleteVoter();
        });
        refreshBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            loadVoters();
        });

        buttonPanel.add(addBtn);
        /*
        ================================================================
        REMOVED DELETE BUTTON
        ================================================================
        */
        //buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void searchVoters() {
        String searchTerm = voterSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadVoters();
            return;
        }

        if (!checkConnectionBeforeOperation()) return;
        
        voterModel.setRowCount(0);
        if (conn != null) {
            try {
                List<Vector<Object>> voters = AdminDatabaseLogic.searchVoters(conn, searchTerm);
                if (voters.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No voters found matching: " + searchTerm);
                } else {
                    for (Vector<Object> row : voters) {
                        voterModel.addRow(row);
                    }
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        searchPanel.setBackground(Color.WHITE);
        
        JLabel searchLabel = new JLabel("Search Statistics:");
        statsSearchField = new JTextField(25);
        statsSearchField.setToolTipText("Search by party name. Examples: 'ANC', 'DA', 'EFF'");
        
        JButton searchBtn = new JButton("Search");
        JButton clearSearchBtn = new JButton("Clear");
        
        searchBtn.addActionListener(e -> searchStats());
        clearSearchBtn.addActionListener(e -> {
            statsSearchField.setText("");
            loadStats();
        });
        
        JPanel searchButtonPanel = new JPanel();
        searchButtonPanel.add(searchBtn);
        searchButtonPanel.add(clearSearchBtn);
        
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(statsSearchField, BorderLayout.CENTER);
        searchPanel.add(searchButtonPanel, BorderLayout.EAST);
        
        panel.add(searchPanel, BorderLayout.NORTH);

        String[] columns = {"Party", "Total Votes", "Votes Today"};
        statsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        statsTable = new JTable(statsModel);
        statsTable.setRowHeight(28);
        panel.add(new JScrollPane(statsTable), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) return;
            loadStats();
        });
        JPanel southPanel = new JPanel();
        southPanel.add(refreshBtn);
        panel.add(southPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void searchStats() {
        String searchTerm = statsSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadStats();
            return;
        }

        if (!checkConnectionBeforeOperation()) return;
        
        statsModel.setRowCount(0);
        if (conn != null) {
            try {
                List<Vector<Object>> allStats = AdminDatabaseLogic.getVoteStatistics(conn);
                List<Vector<Object>> filteredStats = new ArrayList<>();
                
                String lowerSearch = searchTerm.toLowerCase();
                for (Vector<Object> row : allStats) {
                    String party = row.get(0).toString().toLowerCase();
                    if (party.contains(lowerSearch)) {
                        filteredStats.add(row);
                    }
                }
                
                if (filteredStats.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No statistics found for keyword: " + searchTerm);
                } else {
                    for (Vector<Object> row : filteredStats) {
                        statsModel.addRow(row);
                    }
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private void loadStats() {
        if (!checkConnectionBeforeOperation()) return;
        
        statsModel.setRowCount(0);
        if (conn != null) {
            try {
                List<Vector<Object>> stats = AdminDatabaseLogic.getVoteStatistics(conn);
                for (Vector<Object> row : stats) {
                    statsModel.addRow(row);
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private void loadVoters() {
        if (!checkConnectionBeforeOperation()) return;
        
        voterModel.setRowCount(0);
        if (conn != null) {
            try {
                List<Vector<Object>> voters = AdminDatabaseLogic.getAllVoters(conn);
                for (Vector<Object> row : voters) {
                    voterModel.addRow(row);
                }
            } catch (Exception e) {
                handleDatabaseError(e);
            }
        }
    }

    private void deleteVoter() {
        if (!checkConnectionBeforeOperation()) return;
        
        int selectedRow = voterTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a voter to delete.");
            return;
        }
        String idNumber = (String) voterModel.getValueAt(selectedRow, 2);
        try {
            if (AdminDatabaseLogic.deleteVoter(conn, idNumber)) {
                loadVoters();
            }
        } catch (Exception e) {
            handleDatabaseError(e);
        }
    }

    @Override
    public void dispose() {
        if (connectionMonitorTimer != null) {
            connectionMonitorTimer.cancel();
        }
        super.dispose();
    }
}