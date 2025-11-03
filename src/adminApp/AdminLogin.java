package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import javax.swing.border.LineBorder;

public class AdminLogin extends JFrame {

    private Connection conn;
    private JButton loginButton;
    private JLabel connectionStatus;

    public AdminLogin() {
        this.conn = AdminDatabaseConnectivity.getConnection();

        setTitle("Admin Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(new Color(0, 87, 183));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(new Color(0, 87, 183));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.gridx = 0;

        ImageIcon logo = new ImageIcon(getClass().getResource("/appLogo.png"));
        Image scaledImage = logo.getImage().getScaledInstance(250, 250, Image.SCALE_SMOOTH);
        ImageIcon scaledLogo = new ImageIcon(scaledImage);
        JLabel logoLabel = new JLabel(scaledLogo);
        gbc.gridy = 0;
        mainPanel.add(logoLabel, gbc);
        
        gbc.gridy = 1;
        JLabel welcomeLabel = new JLabel(
                "<html><center>Admin Login<br>Electronic Voting System</center></html>",
                SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        welcomeLabel.setForeground(Color.WHITE);
        mainPanel.add(welcomeLabel, gbc);

        gbc.gridy = 2;
        loginButton = new JButton("Login");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 20));
        loginButton.setBorder(new LineBorder(new Color(255, 209, 0)));
        loginButton.setPreferredSize(new Dimension(200, 55));
        mainPanel.add(loginButton, gbc);
        loginButton.addActionListener(e -> {
            loginButton.setEnabled(false);

            AdminDatabaseConnectivity.forceReconnection();

            if (AdminDatabaseConnectivity.isConnectionLost()) {
                JOptionPane.showMessageDialog(AdminLogin.this,
                        "Cannot connect to database. Please check your internet connection and try again.",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                loginButton.setEnabled(true);
                return;
            }

            JDialog scanDialog = createScanDialog();

            new Thread(() -> {
                SwingUtilities.invokeLater(() -> {
                    scanDialog.setVisible(true);
                });

                try {
                    ReaderCollection readers = UareUGlobal.GetReaderCollection();
                    readers.GetReaders();

                    if (readers.size() > 0) {
                        Reader reader = readers.get(0);
                        Connection freshConn = AdminDatabaseConnectivity.getConnection();
                        AdminVerification verification = new AdminVerification(reader, freshConn);
                        verification.startVerification((verified, adminName, adminSurname) -> {
                            SwingUtilities.invokeLater(() -> {
                                scanDialog.dispose();
                                loginButton.setEnabled(true);
                                if (verified) {
                                    AdminDashboard dashboard = new AdminDashboard(freshConn, this);
                                    dashboard.setAdminInfo(adminName, adminSurname);
                                    dashboard.setVisible(true);
                                    dispose();
                                } else {
                                    JOptionPane.showMessageDialog(AdminLogin.this,
                                            "Verification Failed - Access Denied",
                                            "Authentication Error",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            });
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            scanDialog.dispose();
                            JOptionPane.showMessageDialog(AdminLogin.this,
                                    "No fingerprint reader found.",
                                    "Hardware Error",
                                    JOptionPane.ERROR_MESSAGE);
                            loginButton.setEnabled(true);
                        });
                    }
                } catch (UareUException ex) {
                    SwingUtilities.invokeLater(() -> {
                        scanDialog.dispose();
                        JOptionPane.showMessageDialog(AdminLogin.this,
                                "Error accessing fingerprint reader: " + ex.getMessage(),
                                "Hardware Error",
                                JOptionPane.ERROR_MESSAGE);
                        loginButton.setEnabled(true);
                    });
                }
            }).start();
        });

        add(mainPanel);
    }

    public void showLoginAgain() {
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            AdminDatabaseConnectivity.setConnectionLost(true);

            JOptionPane.showMessageDialog(this,
                    "Database connection was lost. Please login again.",
                    "Session Expired",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private JDialog createScanDialog() {
        JDialog scanDialog = new JDialog(this, "Fingerprint Verification", true);
        scanDialog.setLayout(new BorderLayout());
        scanDialog.setSize(400, 200);
        scanDialog.setLocationRelativeTo(this);
        scanDialog.setResizable(false);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        contentPanel.setBackground(Color.WHITE);

        JLabel iconLabel = new JLabel("🔍", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 36));

        JLabel textLabel = new JLabel("<html><center>Please place your finger<br>on the fingerprint scanner</center></html>", SwingConstants.CENTER);
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        contentPanel.add(iconLabel, BorderLayout.NORTH);
        contentPanel.add(textLabel, BorderLayout.CENTER);
        contentPanel.add(progressBar, BorderLayout.SOUTH);

        scanDialog.add(contentPanel);
        return scanDialog;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AdminLogin login = new AdminLogin();
            login.setVisible(true);
        });
    }
}
