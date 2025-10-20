package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import java.awt.*;
import java.sql.Connection;

public class AdminLogin extends JFrame {

    private Connection conn = Database.getConnection();;

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

            JDialog scanDialog = new JDialog(this, "Fingerprint Scan", false);
            scanDialog.setLayout(new BorderLayout());
            scanDialog.add(new JLabel("Place your finger on the scanner...", SwingConstants.CENTER), BorderLayout.CENTER);
            scanDialog.setSize(350, 150);
            scanDialog.setLocationRelativeTo(this);
            scanDialog.setVisible(true);

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
                                this.dispose();
                            } else {
                                JOptionPane.showMessageDialog(this, "Verification Failed",
                                        "Access Denied", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    });
                } else {
                    scanDialog.dispose();
                    JOptionPane.showMessageDialog(this, "No fingerprint reader found.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    loginButton.setEnabled(true);
                }
            } catch (UareUException ex) {
                scanDialog.dispose();
                JOptionPane.showMessageDialog(this, "Error accessing fingerprint reader: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                loginButton.setEnabled(true);
            }
        });

        add(mainPanel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminLogin().setVisible(true));
    }
}
