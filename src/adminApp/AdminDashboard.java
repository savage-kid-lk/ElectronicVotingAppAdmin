package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;

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
    private Timer autoRefreshTimer;

    private JTextField candidateSearchField;
    private JTextField voterSearchField;
    private JTextField statsSearchField;

    // Performance optimization: Thread pool for background tasks
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(3);
    
    // Cache for frequently accessed data - Fixed: Separate cache for each ballot type
    private Map<String, List<Vector<Object>>> cachedCandidatesByTable = new HashMap<>();
    private Map<String, Long> lastCandidateUpdateByTable = new HashMap<>();
    private List<Vector<Object>> cachedVoters = new ArrayList<>();
    private List<Vector<Object>> cachedStats = new ArrayList<>();
    private long lastVoterUpdate = 0;
    private long lastStatsUpdate = 0;
    private static final long CACHE_TIMEOUT = 30000; // 30 seconds
    
    // Loading indicators
    private JProgressBar loadingBar;
    private JDialog loadingDialog;

    private void startAutoRefresh() {
        autoRefreshTimer = new Timer(true);
        autoRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        if (!checkConnectionBeforeOperation()) {
                            return null;
                        }

                        StringBuilder reports = new StringBuilder();
                        reports.append("======= Real-time Fraud Detection =======\n");
                        reports.append("Last Updated: ").append(new java.util.Date()).append("\n\n");

                        if (conn != null) {
                            try {
                                Vector<Object> stats = AdminDatabaseLogic.getVotingStatisticsSummary(conn);
                                List<Vector<Object>> recentFraud = AdminDatabaseLogic.getFraudAttempts(conn);

                                if (stats.size() >= 4) {
                                    reports.append("VOTING STATISTICS:\n");
                                    reports.append("• Total Voters: ").append(stats.get(0)).append("\n");
                                    reports.append("• Total Votes: ").append(stats.get(1)).append("\n");
                                    reports.append("• Votes Today: ").append(stats.get(2)).append("\n");
                                    reports.append("• Total Casted Ballots: ").append(stats.get(3)).append("\n");
                                    reports.append("• Active Fraud Cases: ").append(stats.get(4)).append("\n\n");
                                }

                                if (!recentFraud.isEmpty()) {
                                    reports.append("RECENT FRAUD ATTEMPTS:\n");
                                    int count = 0;
                                    for (Vector<Object> fraud : recentFraud) {
                                        if (count >= 5) {
                                            break;
                                        }
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
                                    reports.append("No fraud attempts detected.\n");
                                }
                            } catch (Exception e) {
                                reports.append("Error loading fraud data: ").append(e.getMessage()).append("\n");
                            }
                        } else {
                            reports.append("Database connection unavailable\n");
                        }

                        final String finalReport = reports.toString();

                        SwingUtilities.invokeLater(() -> {
                            fraudReportsArea.setText(finalReport);
                        });

                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                        } catch (Exception e) {
                            System.err.println("Auto-refresh error: " + e.getMessage());
                        }
                    }
                };

                worker.execute();
            }
        }, 0, 30000);
    }

    public AdminDashboard(Connection connection, AdminLogin loginWindow) {
        this.conn = connection;
        this.loginWindow = loginWindow;
        ImageIcon icon = new ImageIcon(getClass().getResource("/appLogo.png"));
        setIconImage(icon.getImage());
        initializeUI();
        startAutoRefresh();
        startConnectionMonitoring();
        initializeLoadingDialog();
        preloadAllData();
    }

    private void initializeLoadingDialog() {
        loadingDialog = new JDialog(this, "Loading...", false);
        loadingDialog.setLayout(new BorderLayout());
        loadingDialog.setSize(300, 100);
        loadingDialog.setLocationRelativeTo(this);
        loadingDialog.setResizable(false);
        
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel loadingLabel = new JLabel("Loading data, please wait...", SwingConstants.CENTER);
        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        
        contentPanel.add(loadingLabel, BorderLayout.NORTH);
        contentPanel.add(loadingBar, BorderLayout.CENTER);
        loadingDialog.add(contentPanel);
    }

    private void showLoading() {
        SwingUtilities.invokeLater(() -> {
            loadingDialog.setLocationRelativeTo(this);
            loadingDialog.setVisible(true);
        });
    }

    private void hideLoading() {
        SwingUtilities.invokeLater(() -> {
            loadingDialog.setVisible(false);
        });
    }

    private void preloadAllData() {
        backgroundExecutor.execute(() -> {
            try {
                // Preload all ballot types for candidates
                String[] ballotTypes = {"NationalBallot", "RegionalBallot", "ProvincialBallot"};
                for (String ballotType : ballotTypes) {
                    try {
                        List<Vector<Object>> candidates = AdminDatabaseLogic.getAllCandidatesFromTable(conn, ballotType);
                        cachedCandidatesByTable.put(ballotType, candidates);
                        lastCandidateUpdateByTable.put(ballotType, System.currentTimeMillis());
                    } catch (Exception e) {
                        System.err.println("Error preloading " + ballotType + ": " + e.getMessage());
                    }
                }
                
                // Preload voters data
                cachedVoters = AdminDatabaseLogic.getAllVoters(conn);
                lastVoterUpdate = System.currentTimeMillis();
                
                // Preload stats data
                cachedStats = AdminDatabaseLogic.getVoteStatistics(conn);
                lastStatsUpdate = System.currentTimeMillis();
                
            } catch (Exception e) {
                System.err.println("Error preloading data: " + e.getMessage());
            }
        });
    }

    private void invalidateCache() {
        cachedCandidatesByTable.clear();
        lastCandidateUpdateByTable.clear();
        cachedVoters.clear();
        cachedStats.clear();
        preloadAllData(); // Reload data after invalidation
    }

    private void startConnectionMonitoring() {
        connectionMonitorTimer = new Timer(true);
        connectionMonitorTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkConnectionStatus();
            }
        }, 30000, 30000);
    }

    private void checkConnectionStatus() {
        if (AdminDatabaseConnectivity.shouldRedirectToLogin()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(AdminDashboard.this,
                        "Database connection lost. You will be redirected to login.",
                        "Connection Timeout",
                        JOptionPane.WARNING_MESSAGE);
                redirectToLogin();
            });
        }
    }

    private void redirectToLogin() {
        if (connectionMonitorTimer != null) {
            connectionMonitorTimer.cancel();
        }
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
        }
        backgroundExecutor.shutdown();

        dispose();

        if (loginWindow != null) {
            loginWindow.showLoginAgain();
        } else {
            new AdminLogin().setVisible(true);
        }
    }

    private boolean checkConnectionBeforeOperation() {
        if (AdminDatabaseConnectivity.shouldRedirectToLogin()) {
            JOptionPane.showMessageDialog(this,
                    "Database connection lost. Please login again.",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            redirectToLogin();
            return false;
        }
        return true;
    }

    private void handleDatabaseError(Exception e) {
        System.err.println("Database error: " + e.getMessage());

        if (e.getMessage().contains("connection") || e.getMessage().contains("timeout")
                || e.getMessage().contains("closed")) {
            AdminDatabaseConnectivity.setConnectionLost(true);
            redirectToLogin();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Database error: " + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initializeUI() {
        setTitle("Voting Machine Admin Dashboard");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(0, 87, 183));
        setLayout(new BorderLayout());

        header = new JLabel("Electronic Voting Machine Admin Dashboard", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 28));
        header.setForeground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        add(header, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBackground(new Color(0, 87, 183));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel navPanel = new JPanel();
        navPanel.setBackground(new Color(0, 87, 183));
        navPanel.setLayout(new GridLayout(3, 1, 0, 15));
        navPanel.setPreferredSize(new Dimension(280, 0));
        navPanel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JButton manageVotersBtn = createNavButton("Manage Voters");
        JButton manageCandidatesBtn = createNavButton("Manage Candidates");
        JButton viewStatisticsBtn = createNavButton("View Statistics");

        navPanel.add(manageVotersBtn);
        navPanel.add(manageCandidatesBtn);
        navPanel.add(viewStatisticsBtn);
        contentPanel.add(navPanel, BorderLayout.WEST);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));

        JPanel candidatePanel = createCandidatePanel();
        JPanel voterPanel = createVoterPanel();
        JPanel statsPanel = createStatsPanel();

        mainPanel.add(voterPanel, "VOTERS");
        mainPanel.add(candidatePanel, "CANDIDATES");
        mainPanel.add(statsPanel, "STATS");

        contentPanel.add(mainPanel, BorderLayout.CENTER);

        JPanel fraudSidePanel = createFraudReportsPanel();
        fraudSidePanel.setPreferredSize(new Dimension(380, 0));
        contentPanel.add(fraudSidePanel, BorderLayout.EAST);

        add(contentPanel, BorderLayout.CENTER);

        manageVotersBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            loadVotersAsync();
            cardLayout.show(mainPanel, "VOTERS");
        });
        manageCandidatesBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            loadCandidatesAsync();
            cardLayout.show(mainPanel, "CANDIDATES");
        });
        viewStatisticsBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            loadStatsAsync();
            cardLayout.show(mainPanel, "STATS");
        });

        loadCandidatesAsync();
        cardLayout.show(mainPanel, "CANDIDATES");
    }

    private JPanel createFraudReportsPanel() {
        JPanel fraudPanel = new JPanel(new BorderLayout(10, 10));
        fraudPanel.setBackground(new Color(0, 87, 183));
        fraudPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel fraudHeader = new JLabel("Fraud Reports", SwingConstants.CENTER);
        fraudHeader.setFont(new Font("Segoe UI", Font.BOLD, 20));
        fraudHeader.setForeground(Color.WHITE);
        fraudHeader.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        fraudPanel.add(fraudHeader, BorderLayout.NORTH);

        fraudReportsArea = new JTextArea();
        fraudReportsArea.setEditable(false);
        fraudReportsArea.setBackground(new Color(248, 248, 248));
        fraudReportsArea.setForeground(new Color(60, 60, 60));
        fraudReportsArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fraudReportsArea.setLineWrap(true);
        fraudReportsArea.setWrapStyleWord(true);
        fraudReportsArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JScrollPane fraudScrollPane = new JScrollPane(fraudReportsArea);
        fraudScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Recent Activity"
        ));
        fraudScrollPane.setPreferredSize(new Dimension(350, 400));
        fraudPanel.add(fraudScrollPane, BorderLayout.CENTER);

        JButton refreshFraudBtn = new JButton("Refresh Reports");
        refreshFraudBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        refreshFraudBtn.setBackground(new Color(255, 209, 0));
        refreshFraudBtn.setForeground(Color.BLACK);
        refreshFraudBtn.setFocusPainted(false);
        refreshFraudBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 150, 0), 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        refreshFraudBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshFraudBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }

            refreshFraudBtn.setEnabled(false);
            refreshFraudBtn.setText("Refreshing...");

            backgroundExecutor.execute(() -> {
                refreshFraudReports();
                SwingUtilities.invokeLater(() -> {
                    refreshFraudBtn.setEnabled(true);
                    refreshFraudBtn.setText("Refresh Reports");
                });
            });
        });

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logoutBtn.setBackground(new Color(220, 80, 60));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 60, 40), 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to logout?",
                    "Confirm Logout",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                if (connectionMonitorTimer != null) {
                    connectionMonitorTimer.cancel();
                }
                if (autoRefreshTimer != null) {
                    autoRefreshTimer.cancel();
                }
                backgroundExecutor.shutdown();

                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (Exception ex) {
                    System.err.println("Error closing database connection: " + ex.getMessage());
                }

                dispose();
                if (loginWindow != null) {
                    loginWindow.setVisible(true);
                } else {
                    new AdminLogin().setVisible(true);
                }
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setBackground(new Color(0, 87, 183));
        buttonPanel.add(refreshFraudBtn);
        buttonPanel.add(logoutBtn);
        fraudPanel.add(buttonPanel, BorderLayout.SOUTH);

        refreshFraudReports();

        return fraudPanel;
    }

    private void refreshFraudReports() {
        StringBuilder reports = new StringBuilder();
        reports.append("======= Real-time Fraud Detection =======\n");
        reports.append("Last Updated: ").append(new java.util.Date()).append("\n\n");

        if (conn != null) {
            try {
                Vector<Object> stats = AdminDatabaseLogic.getVotingStatisticsSummary(conn);
                if (stats.size() >= 5) {
                    reports.append("VOTING STATISTICS:\n");
                    reports.append("• Total Voters: ").append(stats.get(0)).append("\n");
                    reports.append("• Total Votes: ").append(stats.get(1)).append("\n");
                    reports.append("• Votes Today: ").append(stats.get(2)).append("\n");
                    reports.append("• Total Casted Ballots: ").append(stats.get(3)).append("\n");
                    reports.append("• Active Fraud Cases: ").append(stats.get(4)).append("\n\n");
                }

                List<Vector<Object>> recentFraud = AdminDatabaseLogic.getFraudAttempts(conn);
                if (!recentFraud.isEmpty()) {
                    reports.append("RECENT FRAUD ATTEMPTS:\n");
                    int count = 0;
                    for (Vector<Object> fraud : recentFraud) {
                        if (count >= 5) {
                            break;
                        }
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
                    reports.append("No fraud attempts detected.\n");
                }
            } catch (Exception e) {
                reports.append("Error loading fraud data: ").append(e.getMessage()).append("\n");
            }
        } else {
            reports.append("Database connection unavailable\n");
        }

        final String finalReport = reports.toString();
        SwingUtilities.invokeLater(() -> {
            fraudReportsArea.setText(finalReport);
        });
    }

    public void setAdminInfo(String name, String surname) {
        header.setText("Electronic Voting Machine Admin: " + name + " " + surname);
    }

    private JButton createNavButton(String title) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setBackground(new Color(255, 209, 0));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 150, 0), 2),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(255, 225, 100));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(255, 209, 0));
            }
        });

        return btn;
    }

    private JPanel createCandidatePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBackground(Color.WHITE);

        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Search Candidates"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        searchPanel.setBackground(new Color(248, 248, 248));

        JLabel searchLabel = new JLabel("Search Candidates:");
        searchLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        candidateSearchField = new JTextField(25);
        candidateSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        candidateSearchField.setToolTipText("Search by party name, candidate name, region, or province. Examples: 'EFF', 'Cyril Ramaphosa', 'Gauteng'");
        candidateSearchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));

        JButton searchBtn = createActionButton("Search", new Color(0, 87, 183));
        JButton clearSearchBtn = createActionButton("Clear", new Color(120, 120, 120));
        JButton allCandidatesBtn = createActionButton("Show All Ballots", new Color(0, 87, 183));

        searchBtn.addActionListener(e -> searchCandidatesAsync());
        clearSearchBtn.addActionListener(e -> {
            candidateSearchField.setText("");
            loadCandidatesAsync();
        });
        allCandidatesBtn.addActionListener(e -> searchAllCandidatesAsync());

        JPanel searchButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchButtonPanel.setBackground(new Color(248, 248, 248));
        searchButtonPanel.add(searchBtn);
        searchButtonPanel.add(clearSearchBtn);
        searchButtonPanel.add(allCandidatesBtn);

        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(candidateSearchField, BorderLayout.CENTER);
        searchPanel.add(searchButtonPanel, BorderLayout.EAST);

        categoryTabs = new JPanel(new GridLayout(1, 3, 5, 0));
        categoryTabs.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        categoryTabs.setBackground(Color.WHITE);

        addCategoryTab("NationalBallot");
        addCategoryTab("RegionalBallot");
        addCategoryTab("ProvincialBallot");

        topPanel.add(searchPanel, BorderLayout.NORTH);
        topPanel.add(categoryTabs, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);

        String[] columns = {"Party", "Candidate", "Number of Votes"};
        candidateModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 1;
            }
        };
        candidateTable = new JTable(candidateModel);
        candidateTable.setRowHeight(32);
        candidateTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        candidateTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        candidateTable.getTableHeader().setBackground(new Color(240, 240, 240));
        candidateTable.setSelectionBackground(new Color(220, 237, 255));

        JScrollPane scrollPane = new JScrollPane(candidateTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        panel.add(scrollPane, BorderLayout.CENTER);

        candidateModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && conn != null) {
                if (!checkConnectionBeforeOperation()) {
                    return;
                }

                int row = e.getFirstRow();
                int column = e.getColumn();
                if (row >= 0 && (column == 0 || column == 1)) {
                    String newValue = candidateModel.getValueAt(row, column).toString();

                    String oldParty = candidateModel.getValueAt(row, 0).toString();
                    String oldCandidate = candidateModel.getValueAt(row, 1).toString();

                    String regionOrProvince = "";
                    if (currentTable.equalsIgnoreCase("RegionalBallot")) {
                        regionOrProvince = candidateModel.getValueAt(row, 2).toString();
                    } else if (currentTable.equalsIgnoreCase("ProvincialBallot")) {
                        regionOrProvince = candidateModel.getValueAt(row, 2).toString();
                    }

                    final String finalCurrentTable = currentTable;
                    final String finalNewValue = newValue;
                    final String finalOldParty = oldParty;
                    final String finalOldCandidate = oldCandidate;
                    final String finalRegionOrProvince = regionOrProvince;
                    final int finalColumn = column;

                    backgroundExecutor.execute(() -> {
                        try {
                            AdminDatabaseLogic.updateCandidate(conn, finalCurrentTable, finalColumn, finalNewValue, finalOldParty, finalOldCandidate, finalRegionOrProvince);
                            invalidateCache(); // Reload data after update
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> handleDatabaseError(ex));
                        }
                    });
                }
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(Color.WHITE);

        JButton addBtn = createActionButton("Add Candidate", new Color(0, 87, 183));
        JButton deleteBtn = createActionButton("Delete Candidate", new Color(0, 87, 183));
        JButton updateImageBtn = createActionButton("Update Images", new Color(0, 87, 183));
        JButton refreshBtn = createActionButton("Refresh", new Color(0, 87, 183));

        addBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            addCandidate();
        });
        refreshBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            invalidateCache();
            loadCandidatesAsync();
        });
        deleteBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            deleteCandidate();
        });
        updateImageBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            updateCandidateImages();
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(updateImageBtn);
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void updateCandidateImages() {
        if (!checkConnectionBeforeOperation()) {
            return;
        }

        JDialog dialog = new JDialog(this, "Update Candidate Images", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(Color.WHITE);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JLabel titleLabel = new JLabel("Update Candidate Image", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(0, 87, 183));

        JLabel subtitleLabel = new JLabel("Select a candidate and choose a new photo", JLabel.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(100, 100, 100));

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.CENTER);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        final List<Vector<Object>> candidates = new ArrayList<>();
        try {
            List<Vector<Object>> tempCandidates = AdminDatabaseLogic.getAllCandidatesFromTable(conn, currentTable);
            candidates.addAll(tempCandidates);
        } catch (Exception e) {
            handleDatabaseError(e);
            return;
        }

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(Color.WHITE);
        centerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(220, 220, 220)),
                        "Candidate Selection"
                ),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel candidateLabel = new JLabel("Select Candidate:");
        candidateLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        candidateLabel.setForeground(new Color(60, 60, 60));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        centerPanel.add(candidateLabel, gbc);

        String[] candidateNames = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            Vector<Object> candidate = candidates.get(i);
            String party = (String) candidate.get(0);
            String name = (String) candidate.get(1);
            candidateNames[i] = party + " - " + name;
        }

        JComboBox<String> candidateCombo = new JComboBox<>(candidateNames);
        candidateCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        candidateCombo.setBackground(Color.WHITE);
        candidateCombo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        candidateCombo.setPreferredSize(new Dimension(300, 35));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        centerPanel.add(candidateCombo, gbc);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        JButton selectImageBtn = new JButton("Select New Image");
        selectImageBtn.setBackground(new Color(0, 87, 183));
        selectImageBtn.setForeground(Color.WHITE);
        selectImageBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        selectImageBtn.setFocusPainted(false);
        selectImageBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 67, 143), 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        selectImageBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        selectImageBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                selectImageBtn.setBackground(new Color(0, 107, 203));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                selectImageBtn.setBackground(new Color(0, 87, 183));
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(new Color(120, 120, 120));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100), 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dialog.dispose());

        cancelBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                cancelBtn.setBackground(new Color(140, 140, 140));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                cancelBtn.setBackground(new Color(120, 120, 120));
            }
        });

        buttonPanel.add(selectImageBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);

        selectImageBtn.addActionListener(e -> {
            final int selectedIndex = candidateCombo.getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < candidates.size()) {
                Vector<Object> selectedCandidate = candidates.get(selectedIndex);
                String partyName = (String) selectedCandidate.get(0);
                String candidateName = (String) selectedCandidate.get(1);

                String regionOrProvince = "";
                if (currentTable.equalsIgnoreCase("RegionalBallot") || currentTable.equalsIgnoreCase("ProvincialBallot")) {
                    regionOrProvince = selectedCandidate.size() > 2 ? selectedCandidate.get(2).toString() : "";
                }

                final String finalCurrentTable = currentTable;
                final String finalPartyName = partyName;
                final String finalCandidateName = candidateName;
                final String finalRegionOrProvince = regionOrProvince;

                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select New Candidate Photo for " + candidateName);
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Image files", "jpg", "jpeg", "png", "gif"));

                if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                    try {
                        java.io.File file = chooser.getSelectedFile();
                        byte[] candidateImageData = java.nio.file.Files.readAllBytes(file.toPath());

                        backgroundExecutor.execute(() -> {
                            try {
                                boolean success = AdminDatabaseLogic.updateCandidateImage(
                                        conn,
                                        finalCurrentTable,
                                        finalPartyName,
                                        finalCandidateName,
                                        candidateImageData,
                                        finalRegionOrProvince
                                );

                                SwingUtilities.invokeLater(() -> {
                                    if (success) {
                                        JOptionPane.showMessageDialog(dialog,
                                                "Candidate image updated successfully for " + finalCandidateName + "!",
                                                "Success",
                                                JOptionPane.INFORMATION_MESSAGE);
                                        invalidateCache(); // Reload data after update
                                        dialog.dispose();
                                    } else {
                                        JOptionPane.showMessageDialog(dialog,
                                                "Failed to update candidate image.",
                                                "Error",
                                                JOptionPane.ERROR_MESSAGE);
                                    }
                                });
                            } catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> 
                                    JOptionPane.showMessageDialog(dialog,
                                            "Failed to read image file: " + ex.getMessage(),
                                            "Error",
                                            JOptionPane.ERROR_MESSAGE));
                            }
                        });
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog,
                                "Failed to read image file: " + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(dialog,
                        "Please select a candidate from the list.",
                        "Selection Required",
                        JOptionPane.WARNING_MESSAGE);
            }
        });

        dialog.setVisible(true);
    }

    private JButton createActionButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    private void searchAllCandidatesAsync() {
        String searchTerm = candidateSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a search term.");
            return;
        }

        if (!checkConnectionBeforeOperation()) {
            return;
        }

        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                List<Vector<Object>> candidates = AdminDatabaseLogic.searchCandidates(conn, searchTerm);
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    candidateModel.setRowCount(0);
                    if (candidates.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminDashboard.this, "No candidates found matching: " + searchTerm);
                    } else {
                        candidateModel.setColumnIdentifiers(new String[]{"Party", "Candidate", "Ballot Type", "Votes"});
                        for (Vector<Object> row : candidates) {
                            candidateModel.addRow(row);
                        }
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }

    private void searchCandidatesAsync() {
        String searchTerm = candidateSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadCandidatesAsync();
            return;
        }

        if (!checkConnectionBeforeOperation()) {
            return;
        }

        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                List<Vector<Object>> filteredCandidates = new ArrayList<>();
                String lowerSearch = searchTerm.toLowerCase();

                if (currentTable.equals("NationalBallot")) {
                    String sql = "SELECT nb.party_name, nb.candidate_name, COALESCE(v.vote_count, 0) as votes "
                            + "FROM NationalBallot nb "
                            + "LEFT JOIN (SELECT party_name, COUNT(DISTINCT voter_id_number) as vote_count FROM Votes WHERE category='National' GROUP BY party_name) v "
                            + "ON nb.party_name = v.party_name "
                            + "WHERE LOWER(nb.party_name) LIKE ? OR LOWER(nb.candidate_name) LIKE ?";

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        String searchPattern = "%" + lowerSearch + "%";
                        stmt.setString(1, searchPattern);
                        stmt.setString(2, searchPattern);

                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                Vector<Object> row = new Vector<>();
                                row.add(rs.getString("party_name"));
                                row.add(rs.getString("candidate_name"));
                                row.add(rs.getInt("votes"));
                                filteredCandidates.add(row);
                            }
                        }
                    }

                } else if (currentTable.equals("RegionalBallot")) {
                    String sql = "SELECT rb.party_name, rb.candidate_name, rb.region, COALESCE(v.vote_count, 0) as votes "
                            + "FROM RegionalBallot rb "
                            + "LEFT JOIN (SELECT party_name, COUNT(DISTINCT voter_id_number) as vote_count FROM Votes WHERE category='Regional' GROUP BY party_name) v "
                            + "ON rb.party_name = v.party_name "
                            + "WHERE LOWER(rb.party_name) LIKE ? OR LOWER(rb.candidate_name) LIKE ? OR LOWER(rb.region) LIKE ?";

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        String searchPattern = "%" + lowerSearch + "%";
                        stmt.setString(1, searchPattern);
                        stmt.setString(2, searchPattern);
                        stmt.setString(3, searchPattern);

                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                Vector<Object> row = new Vector<>();
                                row.add(rs.getString("party_name"));
                                row.add(rs.getString("candidate_name"));
                                row.add(rs.getString("region"));
                                row.add(rs.getInt("votes"));
                                filteredCandidates.add(row);
                            }
                        }
                    }

                } else if (currentTable.equals("ProvincialBallot")) {
                    String sql = "SELECT pb.party_name, pb.candidate_name, pb.province, COALESCE(v.vote_count, 0) as votes "
                            + "FROM ProvincialBallot pb "
                            + "LEFT JOIN (SELECT party_name, COUNT(DISTINCT voter_id_number) as vote_count FROM Votes WHERE category='Provincial' GROUP BY party_name) v "
                            + "ON pb.party_name = v.party_name "
                            + "WHERE LOWER(pb.party_name) LIKE ? OR LOWER(pb.candidate_name) LIKE ? OR LOWER(pb.province) LIKE ?";

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        String searchPattern = "%" + lowerSearch + "%";
                        stmt.setString(1, searchPattern);
                        stmt.setString(2, searchPattern);
                        stmt.setString(3, searchPattern);

                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                Vector<Object> row = new Vector<>();
                                row.add(rs.getString("party_name"));
                                row.add(rs.getString("candidate_name"));
                                row.add(rs.getString("province"));
                                row.add(rs.getInt("votes"));
                                filteredCandidates.add(row);
                            }
                        }
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    candidateModel.setRowCount(0);
                    if (filteredCandidates.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminDashboard.this, "No candidates found in " + currentTable.replace("Ballot", "") + " ballot matching: " + searchTerm);
                    } else {
                        String[] columnNames;
                        switch (currentTable) {
                            case "NationalBallot":
                                columnNames = new String[]{"Party", "Candidate", "National Votes"};
                                break;
                            case "RegionalBallot":
                                columnNames = new String[]{"Party", "Candidate", "Region", "Regional Votes"};
                                break;
                            case "ProvincialBallot":
                                columnNames = new String[]{"Party", "Candidate", "Province", "Provincial Votes"};
                                break;
                            default:
                                columnNames = new String[]{"Party", "Candidate", "Number of Votes"};
                        }

                        candidateModel.setColumnIdentifiers(columnNames);

                        for (Vector<Object> row : filteredCandidates) {
                            candidateModel.addRow(row);
                        }
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }

    private void addCategoryTab(String tableName) {
        JButton tab = new JButton(tableName.replace("Ballot", ""));
        tab.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tab.setBackground(Color.LIGHT_GRAY);
        tab.setForeground(Color.BLACK);
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        tab.setFocusPainted(false);
        tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        tab.addActionListener(e -> {
            currentTable = tableName;
            highlightActiveTab(tab);
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            loadCandidatesAsync();
        });

        categoryTabs.add(tab);
        if (categoryTabs.getComponentCount() == 1) {
            highlightActiveTab(tab);
        }
    }

    private void highlightActiveTab(JButton activeTab) {
        for (Component comp : categoryTabs.getComponents()) {
            if (comp instanceof JButton) {
                JButton tab = (JButton) comp;
                if (tab == activeTab) {
                    tab.setBackground(new Color(255, 209, 0));
                    tab.setForeground(Color.BLACK);
                    tab.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                            BorderFactory.createEmptyBorder(10, 20, 10, 20)
                    ));
                    tab.setFont(new Font("Segoe UI", Font.BOLD, 14));
                } else {
                    tab.setBackground(Color.LIGHT_GRAY);
                    tab.setForeground(Color.BLACK);
                    tab.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.GRAY, 1),
                            BorderFactory.createEmptyBorder(10, 20, 10, 20)
                    ));
                    tab.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                }
            }
        }
    }

    private void loadCandidatesAsync() {
        if (!checkConnectionBeforeOperation()) {
            return;
        }

        // Check cache first for the current table
        long currentTime = System.currentTimeMillis();
        List<Vector<Object>> cachedCandidates = cachedCandidatesByTable.get(currentTable);
        Long lastUpdate = lastCandidateUpdateByTable.get(currentTable);
        
        if (cachedCandidates != null && lastUpdate != null && (currentTime - lastUpdate) < CACHE_TIMEOUT) {
            updateCandidateTable(cachedCandidates);
            return;
        }

        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                List<Vector<Object>> candidates = AdminDatabaseLogic.getAllCandidatesFromTable(conn, currentTable);
                // Update cache for this specific table
                cachedCandidatesByTable.put(currentTable, candidates);
                lastCandidateUpdateByTable.put(currentTable, System.currentTimeMillis());
                
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    updateCandidateTable(candidates);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }
    
    private void updateCandidateTable(List<Vector<Object>> candidates) {
        candidateModel.setRowCount(0);
        
        // Set appropriate column headers based on current table
        String[] columnNames;
        switch (currentTable) {
            case "NationalBallot":
                columnNames = new String[]{"Party", "Candidate", "National Votes"};
                break;
            case "RegionalBallot":
                columnNames = new String[]{"Party", "Candidate", "Region", "Regional Votes"};
                break;
            case "ProvincialBallot":
                columnNames = new String[]{"Party", "Candidate", "Province", "Provincial Votes"};
                break;
            default:
                columnNames = new String[]{"Party", "Candidate", "Number of Votes"};
        }
        
        candidateModel.setColumnIdentifiers(columnNames);
        
        for (Vector<Object> row : candidates) {
            candidateModel.addRow(row);
        }
    }

    private void addCandidate() {
        if (!checkConnectionBeforeOperation()) {
            return;
        }
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(Color.WHITE);

        JLabel headerLabel = new JLabel("Add New Candidate", JLabel.CENTER);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        headerLabel.setForeground(new Color(0, 87, 183));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        mainPanel.add(headerLabel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Candidate Details"
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        typePanel.setBackground(Color.WHITE);
        JLabel typeLabel = new JLabel("Candidate Type:");
        typeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JRadioButton partySupportedBtn = new JRadioButton("Party Supported");
        JRadioButton independentBtn = new JRadioButton("Independent Candidate");
        ButtonGroup candidateTypeGroup = new ButtonGroup();
        candidateTypeGroup.add(partySupportedBtn);
        candidateTypeGroup.add(independentBtn);
        partySupportedBtn.setSelected(true);

        partySupportedBtn.setBackground(Color.WHITE);
        independentBtn.setBackground(Color.WHITE);
        partySupportedBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        independentBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        typePanel.add(typeLabel);
        typePanel.add(partySupportedBtn);
        typePanel.add(independentBtn);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        formPanel.add(typePanel, gbc);

        JLabel partyLabel = new JLabel("Party Abbreviation:");
        partyLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JTextField partyField = new JTextField(20);
        partyField.setToolTipText("Enter party abbreviation (e.g., ANC, DA, EFF)");

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        formPanel.add(partyLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        formPanel.add(partyField, gbc);

        JLabel nameLabel = new JLabel("Candidate Full Name:");
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JTextField candidateNameField = new JTextField(20);
        candidateNameField.setToolTipText("Enter candidate's full name");

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        formPanel.add(nameLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(candidateNameField, gbc);

        JPanel ballotPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        ballotPanel.setBackground(Color.WHITE);
        JLabel ballotLabel = new JLabel("Select Ballots:");
        ballotLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JCheckBox nationalBox = new JCheckBox("National");
        JCheckBox regionalBox = new JCheckBox("Regional");
        JCheckBox provincialBox = new JCheckBox("Provincial");

        nationalBox.setBackground(Color.WHITE);
        regionalBox.setBackground(Color.WHITE);
        provincialBox.setBackground(Color.WHITE);
        nationalBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        regionalBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        provincialBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        ballotPanel.add(ballotLabel);
        ballotPanel.add(nationalBox);
        ballotPanel.add(regionalBox);
        ballotPanel.add(provincialBox);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        formPanel.add(ballotPanel, gbc);

        String[] regions = {"Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape",
            "Free State", "Limpopo", "Mpumalanga", "North West", "Northern Cape"};
        String[] provinces = regions;

        JLabel regionLabel = new JLabel("Region:");
        regionLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JComboBox<String> regionDropdown = new JComboBox<>(regions);
        regionDropdown.setEnabled(false);

        JLabel provinceLabel = new JLabel("Province:");
        provinceLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JComboBox<String> provinceDropdown = new JComboBox<>(provinces);
        provinceDropdown.setEnabled(false);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        formPanel.add(regionLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(regionDropdown, gbc);
        gbc.gridx = 0;
        gbc.gridy = 5;
        formPanel.add(provinceLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(provinceDropdown, gbc);

        JPanel imagePanel = new JPanel(new GridLayout(2, 2, 10, 10));
        imagePanel.setBackground(Color.WHITE);
        imagePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Upload Images"
        ));

        JButton partyLogoButton = createImageButton("Select Party Logo", new Color(0, 87, 183));
        JButton candidateFaceButton = createImageButton("Select Candidate Photo", new Color(0, 87, 183));

        JLabel partyLogoLabel = new JLabel("No logo selected", JLabel.CENTER);
        JLabel candidateFaceLabel = new JLabel("No photo selected", JLabel.CENTER);

        partyLogoLabel.setForeground(Color.GRAY);
        candidateFaceLabel.setForeground(Color.GRAY);
        partyLogoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        candidateFaceLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        imagePanel.add(partyLogoButton);
        imagePanel.add(candidateFaceButton);
        imagePanel.add(partyLogoLabel);
        imagePanel.add(candidateFaceLabel);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        formPanel.add(imagePanel, gbc);

        final byte[][] partyLogoData = new byte[1][1];
        final byte[][] candidateFaceData = new byte[1][1];

        partyLogoButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Party Logo");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Image files", "jpg", "jpeg", "png", "gif"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.io.File file = chooser.getSelectedFile();
                    partyLogoData[0] = java.nio.file.Files.readAllBytes(file.toPath());
                    partyLogoLabel.setText(file.getName());
                    partyLogoLabel.setForeground(Color.BLACK);
                    partyLogoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to read party logo: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        candidateFaceButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Candidate Photo");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Image files", "jpg", "jpeg", "png", "gif"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.io.File file = chooser.getSelectedFile();
                    candidateFaceData[0] = java.nio.file.Files.readAllBytes(file.toPath());
                    candidateFaceLabel.setText(file.getName());
                    candidateFaceLabel.setForeground(Color.BLACK);
                    candidateFaceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to read candidate image: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        mainPanel.add(formPanel, BorderLayout.CENTER);

        ActionListener toggleFields = e -> {
            boolean isParty = partySupportedBtn.isSelected();
            partyLabel.setVisible(isParty);
            partyField.setVisible(isParty);
            partyLogoButton.setVisible(isParty);
            partyLogoLabel.setVisible(isParty);

            boolean showRegion = regionalBox.isSelected();
            boolean showProvince = provincialBox.isSelected();

            regionLabel.setVisible(showRegion);
            regionDropdown.setVisible(showRegion);
            regionDropdown.setEnabled(showRegion);

            provinceLabel.setVisible(showProvince);
            provinceDropdown.setVisible(showProvince);
            provinceDropdown.setEnabled(showProvince);

            mainPanel.revalidate();
            mainPanel.repaint();
        };

        partySupportedBtn.addActionListener(toggleFields);
        independentBtn.addActionListener(toggleFields);
        regionalBox.addActionListener(toggleFields);
        provincialBox.addActionListener(toggleFields);

        toggleFields.actionPerformed(null);

        int result = JOptionPane.showConfirmDialog(this, mainPanel,
                "Add New Candidate - Voting System",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            if (candidateNameField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter candidate name.",
                        "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (partySupportedBtn.isSelected() && partyField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter party abbreviation for party-supported candidate.",
                        "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (candidateFaceData[0] == null || candidateFaceData[0].length == 0) {
                JOptionPane.showMessageDialog(this,
                        "Please select a candidate photo.",
                        "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!nationalBox.isSelected() && !regionalBox.isSelected() && !provincialBox.isSelected()) {
                JOptionPane.showMessageDialog(this,
                        "Please select at least one ballot type.",
                        "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String partyName = partySupportedBtn.isSelected() ? partyField.getText().trim() : "Independent";
            String candidateName = candidateNameField.getText().trim();
            String value = "";
            if (regionalBox.isSelected()) {
                value = regionDropdown.getSelectedItem().toString();
            } else if (provincialBox.isSelected()) {
                value = provinceDropdown.getSelectedItem().toString();
            }

            final String finalPartyName = partyName;
            final String finalCandidateName = candidateName;
            final String finalValue = value;
            final boolean finalNationalSelected = nationalBox.isSelected();
            final boolean finalRegionalSelected = regionalBox.isSelected();
            final boolean finalProvincialSelected = provincialBox.isSelected();
            final byte[] finalCandidateFaceData = candidateFaceData[0];
            final byte[] finalPartyLogoData = partySupportedBtn.isSelected() ? partyLogoData[0] : null;
            final boolean finalIsIndependent = !partySupportedBtn.isSelected();

            showLoading();
            backgroundExecutor.execute(() -> {
                try {
                    boolean added = AdminDatabaseLogic.addCandidateToBallots(
                            conn,
                            finalPartyName,
                            finalCandidateName,
                            finalNationalSelected,
                            finalRegionalSelected,
                            finalProvincialSelected,
                            finalCandidateFaceData,
                            finalPartyLogoData,
                            finalIsIndependent,
                            finalValue
                    );

                    SwingUtilities.invokeLater(() -> {
                        hideLoading();
                        if (added) {
                            invalidateCache(); // Reload data after adding
                            loadCandidatesAsync();
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        hideLoading();
                        handleDatabaseError(ex);
                    });
                }
            });
        }
    }

    private JButton createImageButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    private void deleteCandidate() {
        if (!checkConnectionBeforeOperation()) {
            return;
        }

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
            showLoading();
            backgroundExecutor.execute(() -> {
                try {
                    boolean deleted = AdminDatabaseLogic.deleteCandidateFromTable(conn, currentTable, partyName, candidateName);
                    SwingUtilities.invokeLater(() -> {
                        hideLoading();
                        if (deleted) {
                            invalidateCache(); // Reload data after deletion
                            loadCandidatesAsync();
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        hideLoading();
                        handleDatabaseError(e);
                    });
                }
            });
        }
    }

    private JPanel createVoterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Search Voters"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        searchPanel.setBackground(new Color(248, 248, 248));

        JLabel searchLabel = new JLabel("Search Voters:");
        searchLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        voterSearchField = new JTextField(25);
        voterSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        voterSearchField.setToolTipText("Search by name, surname, ID number, voting status. Examples: 'John', '990101', 'voted', 'not voted'");
        voterSearchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));

        JButton searchBtn = createActionButton("Search", new Color(0, 87, 183));
        JButton clearSearchBtn = createActionButton("Clear", new Color(120, 120, 120));

        searchBtn.addActionListener(e -> searchVotersAsync());
        clearSearchBtn.addActionListener(e -> {
            voterSearchField.setText("");
            loadVotersAsync();
        });

        JPanel searchButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchButtonPanel.setBackground(new Color(248, 248, 248));
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
        voterTable.setRowHeight(32);
        voterTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        voterTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        voterTable.getTableHeader().setBackground(new Color(240, 240, 240));
        voterTable.setSelectionBackground(new Color(220, 237, 255));

        JScrollPane scrollPane = new JScrollPane(voterTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        panel.add(scrollPane, BorderLayout.CENTER);

        voterModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && conn != null) {
                if (!checkConnectionBeforeOperation()) {
                    return;
                }

                int row = e.getFirstRow();
                int column = e.getColumn();
                if (row >= 0 && (column == 0 || column == 1)) {
                    String idNumber = voterModel.getValueAt(row, 2).toString();
                    String newValue = voterModel.getValueAt(row, column).toString();
                    
                    final String finalIdNumber = idNumber;
                    final String finalNewValue = newValue;
                    final int finalColumn = column;
                    
                    backgroundExecutor.execute(() -> {
                        try {
                            AdminDatabaseLogic.updateVoter(conn, finalIdNumber, finalColumn, finalNewValue);
                            invalidateCache(); // Reload data after update
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> handleDatabaseError(ex));
                        }
                    });
                }
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(Color.WHITE);

        JButton addBtn = createActionButton("Add Voter", new Color(0, 87, 183));
        JButton deleteBtn = createActionButton("Delete Voter", new Color(0, 87, 183));
        JButton refreshBtn = createActionButton("Refresh", new Color(0, 87, 183));

        addBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            try {
                ReaderCollection collection = UareUGlobal.GetReaderCollection();
                collection.GetReaders();
                Reader reader = collection.size() > 0 ? collection.get(0) : null;
                if (reader == null) {
                    JOptionPane.showMessageDialog(this, "No fingerprint reader found!");
                    return;
                }
                AddVoters.Run(reader, conn);
                invalidateCache(); // Reload data after adding voter
                loadVotersAsync();
                UareUGlobal.DestroyReaderCollection();
            } catch (UareUException ex) {
                ex.printStackTrace();
            } catch (Exception ex) {
                handleDatabaseError(ex);
            }
        });

        refreshBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            invalidateCache();
            loadVotersAsync();
        });

        deleteBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            deleteVoter();
        });

        buttonPanel.add(addBtn);
        //buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void searchVotersAsync() {
        String searchTerm = voterSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadVotersAsync();
            return;
        }

        if (!checkConnectionBeforeOperation()) {
            return;
        }

        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                List<Vector<Object>> voters = AdminDatabaseLogic.searchVoters(conn, searchTerm);
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    voterModel.setRowCount(0);
                    if (voters.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminDashboard.this, "No voters found matching: " + searchTerm);
                    } else {
                        for (Vector<Object> row : voters) {
                            voterModel.addRow(row);
                        }
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Search Statistics"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        searchPanel.setBackground(new Color(248, 248, 248));

        JLabel searchLabel = new JLabel("Search Statistics:");
        searchLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statsSearchField = new JTextField(25);
        statsSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statsSearchField.setToolTipText("Search by party name. Examples: 'ANC', 'DA', 'EFF'");
        statsSearchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));

        JButton searchBtn = createActionButton("Search", new Color(0, 87, 183));
        JButton clearSearchBtn = createActionButton("Clear", new Color(120, 120, 120));

        searchBtn.addActionListener(e -> searchStatsAsync());
        clearSearchBtn.addActionListener(e -> {
            statsSearchField.setText("");
            loadStatsAsync();
        });

        JPanel searchButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchButtonPanel.setBackground(new Color(248, 248, 248));
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
        statsTable.setRowHeight(32);
        statsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        statsTable.getTableHeader().setBackground(new Color(240, 240, 240));
        statsTable.setSelectionBackground(new Color(220, 237, 255));

        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton refreshBtn = createActionButton("Refresh", new Color(0, 87, 183));
        refreshBtn.addActionListener(e -> {
            if (!checkConnectionBeforeOperation()) {
                return;
            }
            invalidateCache();
            loadStatsAsync();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void searchStatsAsync() {
        String searchTerm = statsSearchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadStatsAsync();
            return;
        }

        if (!checkConnectionBeforeOperation()) {
            return;
        }

        showLoading();
        backgroundExecutor.execute(() -> {
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

                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    statsModel.setRowCount(0);
                    if (filteredStats.isEmpty()) {
                        JOptionPane.showMessageDialog(AdminDashboard.this, "No statistics found for keyword: " + searchTerm);
                    } else {
                        for (Vector<Object> row : filteredStats) {
                            statsModel.addRow(row);
                        }
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }

    private void loadStatsAsync() {
        if (!checkConnectionBeforeOperation()) {
            return;
        }

        // Check cache first
        long currentTime = System.currentTimeMillis();
        if (!cachedStats.isEmpty() && (currentTime - lastStatsUpdate) < CACHE_TIMEOUT) {
            updateStatsTableFromCache();
            return;
        }

        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                List<Vector<Object>> stats = AdminDatabaseLogic.getVoteStatistics(conn);
                cachedStats = stats;
                lastStatsUpdate = System.currentTimeMillis();
                
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    updateStatsTableFromCache();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }
    
    private void updateStatsTableFromCache() {
        statsModel.setRowCount(0);
        for (Vector<Object> row : cachedStats) {
            statsModel.addRow(row);
        }
    }

    private void loadVotersAsync() {
        if (!checkConnectionBeforeOperation()) {
            return;
        }

        // Check cache first
        long currentTime = System.currentTimeMillis();
        if (!cachedVoters.isEmpty() && (currentTime - lastVoterUpdate) < CACHE_TIMEOUT) {
            updateVoterTableFromCache();
            return;
        }

        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                List<Vector<Object>> voters = AdminDatabaseLogic.getAllVoters(conn);
                cachedVoters = voters;
                lastVoterUpdate = System.currentTimeMillis();
                
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    updateVoterTableFromCache();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }
    
    private void updateVoterTableFromCache() {
        voterModel.setRowCount(0);
        for (Vector<Object> row : cachedVoters) {
            voterModel.addRow(row);
        }
    }

    private void deleteVoter() {
        if (!checkConnectionBeforeOperation()) {
            return;
        }

        int selectedRow = voterTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a voter to delete.");
            return;
        }
        String idNumber = (String) voterModel.getValueAt(selectedRow, 2);
        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                boolean deleted = AdminDatabaseLogic.deleteVoter(conn, idNumber);
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    if (deleted) {
                        invalidateCache(); // Reload data after deletion
                        loadVotersAsync();
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hideLoading();
                    handleDatabaseError(e);
                });
            }
        });
    }

    @Override
    public void dispose() {
        if (connectionMonitorTimer != null) {
            connectionMonitorTimer.cancel();
        }
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
        }
        backgroundExecutor.shutdown();
        super.dispose();
    }
}