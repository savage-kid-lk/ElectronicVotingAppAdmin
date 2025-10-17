package adminApp;

import com.digitalpersona.uareu.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.SQLException;

public class EnrollmentGUI extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;

    private CaptureThread m_capture;
    private Reader m_reader;
    private JDialog m_dlgParent;
    private JLabel namelJLabel, surnameLabel, idLabel;
    private JTextField nameField, surnameField, idField;
    private JPanel buttonPanel;
    private JDialog fingerDialogue;

    // ✅ Single persistent database connection
    private static Connection conn;

    public EnrollmentGUI(Reader reader) {
        this.m_reader = reader;

        // ✅ Initialize UI layout
        BoxLayout layout = new BoxLayout(this, BoxLayout.Y_AXIS);
        setLayout(layout);
        setOpaque(true);

        add(Box.createVerticalStrut(10));

        // --- Input fields ---
        namelJLabel = new JLabel("Enter Name");
        nameField = new JTextField(20);
        surnameLabel = new JLabel("Enter Surname");
        surnameField = new JTextField(20);
        idLabel = new JLabel("Enter ID Number");
        idField = new JTextField(20);

        add(namelJLabel);
        add(nameField);
        add(surnameLabel);
        add(surnameField);
        add(idLabel);
        add(idField);

        // --- Buttons ---
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton btnBack = new JButton("⬅ Back");
        btnBack.addActionListener(e -> {
            StopCaptureThread();
            if (m_dlgParent != null) m_dlgParent.dispose();
        });

        JButton btnCapture = new JButton("🖐 Capture Fingerprint");
        btnCapture.addActionListener(e -> captureFingerPrint());

        buttonPanel.add(btnCapture);
        buttonPanel.add(btnBack);

        add(Box.createVerticalStrut(10));
        add(buttonPanel);
    }

    // ---------------------- CAPTURE THREAD HANDLING ----------------------
    private void StartCaptureThread() {
        if (m_reader == null) {
            JOptionPane.showMessageDialog(this, "❌ No fingerprint reader detected!");
            return;
        }

        m_capture = new CaptureThread(m_reader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT);
        m_capture.start(this);
    }

    private void StopCaptureThread() {
        if (m_capture != null) {
            m_capture.cancel();
        }
    }

    private void WaitForCaptureThread() {
        if (m_capture != null) {
            m_capture.join(1000);
        }
    }

    // ---------------------- FINGERPRINT CAPTURE ----------------------
    private void captureFingerPrint() {
        try {
            if (m_reader == null) {
                JOptionPane.showMessageDialog(this, "❌ No fingerprint reader detected!");
                return;
            }

            // Open the reader when capture starts
            try {
                m_reader.Open(Reader.Priority.COOPERATIVE);
                System.out.println("✅ Reader opened successfully.");
            } catch (UareUException e) {
                JOptionPane.showMessageDialog(this, "❌ Failed to open reader: " + e.getMessage());
                return;
            }

            // Show capture dialog
            fingerDialogue = new JDialog((Frame) null, "Fingerprint Capture", false);
            fingerDialogue.setSize(250, 100);
            fingerDialogue.setLocationRelativeTo(null);
            fingerDialogue.add(new JLabel("Place your finger on the scanner...", SwingConstants.CENTER));
            fingerDialogue.setVisible(true);

            // Start capture thread
            m_capture = new CaptureThread(m_reader, false, Fid.Format.ANSI_381_2004,
                    Reader.ImageProcessing.IMG_PROC_DEFAULT);

            m_capture.start(evt -> {
                CaptureThread.CaptureEvent captureEvt = (CaptureThread.CaptureEvent) evt;
                if (captureEvt.capture_result != null) {
                    System.out.println("🧩 Capture quality: " + captureEvt.capture_result.quality);

                    if (captureEvt.capture_result.quality == Reader.CaptureQuality.GOOD) {
                        AdminDatabaseLogic.saveVoter(
                                conn,
                                captureEvt.capture_result.image.getData(),
                                nameField.getText().trim(),
                                surnameField.getText().trim(),
                                idField.getText().trim()
                        );

                        fingerDialogue.dispose();
                        JOptionPane.showMessageDialog(this, "✅ Voter fingerprint and info saved successfully!");
                        StopCaptureThread();

                        try {
                            m_reader.Close();
                            System.out.println("🔒 Reader closed.");
                        } catch (UareUException ignored) {
                        }

                        if (m_dlgParent != null) m_dlgParent.dispose();

                    } else {
                        JOptionPane.showMessageDialog(this, "❌ Poor quality. Please try again.");
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------------- DIALOG SETUP ----------------------
    private void doModal(JDialog dlgParent) {
        if (dlgParent == null) {
            dlgParent = new JDialog((Frame) null, "Voter Enrollment", true);
        }

        m_dlgParent = dlgParent;

        // Reader not opened yet — will open when capture button is pressed
        m_dlgParent.setContentPane(this);
        m_dlgParent.pack();
        m_dlgParent.setLocationRelativeTo(null);
        m_dlgParent.toFront();
        m_dlgParent.setVisible(true);

        WaitForCaptureThread();
    }

    // ---------------------- RUN FROM DASHBOARD ----------------------
    public static void Run(Reader reader) {
        try {
            // ✅ Connect to DB only once
            if (conn == null || conn.isClosed()) {
                conn = Database.getConnection();
                if (conn != null) {
                    System.out.println("✅ Database connection established successfully!");
                } else {
                    System.out.println("❌ Failed to establish database connection!");
                    return;
                }
            }

            // Initialize reader collection
            ReaderCollection collection = UareUGlobal.GetReaderCollection();
            collection.GetReaders();

            if (collection.size() > 0) {
                Reader selectedReader = reader != null ? reader : collection.get(0);
                System.out.println("🖐 Using reader: " + selectedReader.GetDescription().name);

                JDialog dlg = new JDialog((Frame) null, "Voter Enrollment", true);
                EnrollmentGUI gui = new EnrollmentGUI(selectedReader);
                gui.doModal(dlg);
            } else {
                JOptionPane.showMessageDialog(null, "❌ No fingerprint readers found!");
            }

            UareUGlobal.DestroyReaderCollection();

        } catch (UareUException | SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Not used
    }
}
