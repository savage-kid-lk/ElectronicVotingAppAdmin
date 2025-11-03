package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.SQLException;

public class AddVoters extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;

    private CaptureThread captureThread;
    private Reader reader;
    private JDialog parentDialog;
    private JLabel nameLabel, surnameLabel, idLabel;
    private JTextField nameField, surnameField, idField;
    private JPanel buttonPanel;
    private JDialog fingerprintDialog;
    private static Connection databaseConnection;

    public AddVoters(Reader reader, Connection connection) {
        this.reader = reader;
        databaseConnection = connection;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Header
        JLabel headerLabel = new JLabel("Voter Enrollment", JLabel.CENTER);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        headerLabel.setForeground(new Color(0, 87, 183));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        add(headerLabel, BorderLayout.NORTH);

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Voter Information"
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name field
        nameLabel = new JLabel("Full Name:");
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        nameField = new JTextField(20);
        nameField.setToolTipText("Enter voter's first name");

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(nameLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(nameField, gbc);

        // Surname field
        surnameLabel = new JLabel("Surname:");
        surnameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        surnameField = new JTextField(20);
        surnameField.setToolTipText("Enter voter's surname");

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(surnameLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(surnameField, gbc);

        // ID field
        idLabel = new JLabel("ID Number:");
        idLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        idField = new JTextField(20);
        idField.setToolTipText("Enter 13-digit South African ID number");

        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(idLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(idField, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Button panel
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JButton captureButton = createStyledButton("Capture Fingerprint", new Color(65, 168, 95));
        captureButton.addActionListener(e -> captureFingerprint());

        JButton backButton = createStyledButton("Back", new Color(220, 80, 60));
        backButton.addActionListener(e -> {
            stopCaptureThread();
            if (parentDialog != null) {
                parentDialog.dispose();
            }
        });

        buttonPanel.add(captureButton);
        buttonPanel.add(backButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.darker());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    private void stopCaptureThread() {
        if (captureThread != null) {
            try {
                captureThread.cancel();
                captureThread.join();
                System.out.println("Capture thread stopped successfully.");
            } catch (InterruptedException e) {
                System.err.println("Interrupted while stopping capture: " + e.getMessage());
            }
        }

        if (reader != null) {
            try {
                reader.Close();
                System.out.println("Reader closed successfully.");
            } catch (UareUException ignored) {
            }
        }
    }

    private void captureFingerprint() {
        // Validate input fields
        if (nameField.getText().trim().isEmpty()
                || surnameField.getText().trim().isEmpty()
                || idField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all voter information fields.",
                    "Incomplete Information",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!AdminDatabaseLogic.isValidSouthAfricanID(idField.getText().trim())) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid South African ID number.",
                    "Invalid ID Number",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (reader == null) {
                JOptionPane.showMessageDialog(this,
                        "No fingerprint reader detected!",
                        "Hardware Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                reader.Open(Reader.Priority.COOPERATIVE);
                System.out.println("Reader opened successfully.");
            } catch (UareUException e) {
                JOptionPane.showMessageDialog(this,
                        "Failed to open fingerprint reader: " + e.getMessage(),
                        "Reader Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            fingerprintDialog = new JDialog(parentDialog, "Fingerprint Capture", false); // Changed to false
            fingerprintDialog.setLayout(new BorderLayout());
            fingerprintDialog.setSize(400, 180);
            fingerprintDialog.setLocationRelativeTo(parentDialog);
            fingerprintDialog.setResizable(false);

            JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
            contentPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            contentPanel.setBackground(Color.WHITE);

            JLabel iconLabel = new JLabel("", SwingConstants.CENTER);
            iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 36));

            JLabel instructionLabel = new JLabel("<html><center>Please place your finger<br>on the fingerprint scanner</center></html>", SwingConstants.CENTER);
            instructionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            instructionLabel.setForeground(new Color(80, 80, 80));

            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setPreferredSize(new Dimension(300, 20));

            contentPanel.add(iconLabel, BorderLayout.NORTH);
            contentPanel.add(instructionLabel, BorderLayout.CENTER);
            contentPanel.add(progressBar, BorderLayout.SOUTH);

            fingerprintDialog.add(contentPanel);
            fingerprintDialog.setVisible(true);

            // Start capture thread
            captureThread = new CaptureThread(reader, false, Fid.Format.ANSI_381_2004,
                    Reader.ImageProcessing.IMG_PROC_DEFAULT);

            captureThread.start(evt -> {
                CaptureThread.CaptureEvent captureEvent = (CaptureThread.CaptureEvent) evt;
                if (captureEvent.capture_result != null) {
                    System.out.println("Capture quality: " + captureEvent.capture_result.quality);

                    if (captureEvent.capture_result.quality == Reader.CaptureQuality.GOOD) {
                        boolean success = AdminDatabaseLogic.saveVoter(
                                databaseConnection,
                                captureEvent.capture_result.image.getData(),
                                nameField.getText().trim(),
                                surnameField.getText().trim(),
                                idField.getText().trim()
                        );

                        if (success) {
                            clearForm();
                            if (parentDialog != null) {
                                parentDialog.dispose();
                            }
                        }
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Fingerprint quality is poor. Please try again.",
                                "Capture Quality",
                                JOptionPane.WARNING_MESSAGE);
                    }

                    fingerprintDialog.dispose();
                    stopCaptureThread();
                }
            });

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error during fingerprint capture: " + e.getMessage(),
                    "Capture Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void clearForm() {
        nameField.setText("");
        surnameField.setText("");
        idField.setText("");
    }

    private void showDialog(JDialog dialog) {
        if (dialog == null) {
            dialog = new JDialog((Frame) null, "Voter Enrollment", true);
        }

        parentDialog = dialog;
        parentDialog.setContentPane(this);
        parentDialog.pack();
        parentDialog.setLocationRelativeTo(null);
        parentDialog.toFront();
        parentDialog.setVisible(true);
    }

    public static void Run(Reader reader, Connection connection) {
        try {
            if (connection == null || connection.isClosed()) {
                connection = AdminDatabaseConnectivity.getConnection();
                if (connection != null) {
                    System.out.println("Database connection established successfully!");
                } else {
                    System.out.println("Failed to establish database connection!");
                    return;
                }
            }

            databaseConnection = connection;

            ReaderCollection collection = UareUGlobal.GetReaderCollection();
            collection.GetReaders();

            if (collection.size() > 0) {
                Reader selectedReader = reader != null ? reader : collection.get(0);
                System.out.println("Using reader: " + selectedReader.GetDescription().name);

                JDialog dialog = new JDialog((Frame) null, "Voter Enrollment", true);
                AddVoters gui = new AddVoters(selectedReader, databaseConnection);
                gui.showDialog(dialog);
            } else {
                JOptionPane.showMessageDialog(null,
                        "No fingerprint readers found! Please connect a fingerprint scanner.",
                        "Hardware Not Found",
                        JOptionPane.ERROR_MESSAGE);
            }

            UareUGlobal.DestroyReaderCollection();

        } catch (UareUException | SQLException e) {
            JOptionPane.showMessageDialog(null,
                    "System error: " + e.getMessage(),
                    "Initialization Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Implementation not required for current functionality
    }
}
