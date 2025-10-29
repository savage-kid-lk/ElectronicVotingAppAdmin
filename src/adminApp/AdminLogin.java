package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import java.awt.*;
import java.sql.Connection;

public class AdminLogin extends JFrame {

    private Connection conn = AdminDatabaseConnectivity.getConnection();;

    public AdminLogin() {

        setTitle("Admin Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(new Color(0, 87, 183));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(new Color(0, 87, 183));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);

        JLabel welcomeLabel = new JLabel(
                "<html><center>Admin Fingerprint Login<br>Electronic Voting System</center></html>",
                SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        welcomeLabel.setForeground(Color.WHITE);
        mainPanel.add(welcomeLabel, gbc);

        JButton loginButton = new JButton("Login");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 20));
        loginButton.setBackground(new Color(255, 209, 0));
        loginButton.setPreferredSize(new Dimension(200, 55));

        gbc.gridy = 1;
        mainPanel.add(loginButton, gbc);

        loginButton.addActionListener(e -> {
            loginButton.setEnabled(false);

            JDialog scanDialog = createScanDialog();
            
            // Start verification in separate thread
            new Thread(() -> {
                SwingUtilities.invokeLater(() -> {
                    scanDialog.setVisible(true);
                });
                
                try {
                    ReaderCollection readers = UareUGlobal.GetReaderCollection();
                    readers.GetReaders();

                    if (readers.size() > 0) {
                        Reader reader = readers.get(0);
                        AdminVerification verification = new AdminVerification(reader, conn);
                        verification.startVerification((verified, adminName, adminSurname) -> {
                            SwingUtilities.invokeLater(() -> {
                                scanDialog.dispose();
                                loginButton.setEnabled(true);
                                if (verified) {
                                    // Pass the single connection to the dashboard
                                    AdminDashboard dashboard = new AdminDashboard(conn);
                                    dashboard.setAdminInfo(adminName, adminSurname);
                                    dashboard.setVisible(true);
                                    dispose();
                                } else {
                                    JOptionPane.showMessageDialog(AdminLogin.this, 
                                        "❌ Verification Failed - Access Denied", 
                                        "Authentication Error", 
                                        JOptionPane.ERROR_MESSAGE);
                                }
                            });
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            scanDialog.dispose();
                            JOptionPane.showMessageDialog(AdminLogin.this, 
                                "❌ No fingerprint reader found.", 
                                "Hardware Error", 
                                JOptionPane.ERROR_MESSAGE);
                            loginButton.setEnabled(true);
                        });
                    }
                } catch (UareUException ex) {
                    SwingUtilities.invokeLater(() -> {
                        scanDialog.dispose();
                        JOptionPane.showMessageDialog(AdminLogin.this, 
                            "❌ Error accessing fingerprint reader: " + ex.getMessage(),
                            "Hardware Error", 
                            JOptionPane.ERROR_MESSAGE);
                        loginButton.setEnabled(true);
                    });
                }
            }).start();
        });

        add(mainPanel);
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
        SwingUtilities.invokeLater(() -> new AdminLogin().setVisible(true));
    }
}