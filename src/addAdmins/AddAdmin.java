package addAdmins;

import adminApp.AdminDatabaseLogic;
import adminApp.CaptureThread;
import adminApp.AdminDatabaseConnectivity;
import com.digitalpersona.uareu.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;

public class AddAdmin extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;
    private CaptureThread m_capture;
    private Reader m_reader;
    private JDialog m_dlgParent;
    private JLabel namelJLabel, surnameLabel, idLabel;
    private JTextField nameField, surnameField, idField;
    private JPanel buttonPanel;
    private JDialog fingerDialogue;

    // AdminDatabaseConnectivity connection (kept open for this session)
    private static Connection conn;

    public AddAdmin(Reader reader) {
        this.m_reader = reader;

        // Initialize UI layout
        BoxLayout layout = new BoxLayout(this, BoxLayout.Y_AXIS);
        setLayout(layout);
        setOpaque(true);

        // User info input fields
        namelJLabel = new JLabel("Enter Name");
        nameField = new JTextField();
        surnameLabel = new JLabel("Enter Surname");
        surnameField = new JTextField();
        idLabel = new JLabel("Enter ID Number");
        idField = new JTextField();

        add(namelJLabel);
        add(nameField);
        add(surnameLabel);
        add(surnameField);
        add(idLabel);
        add(idField);

        // Buttons panel
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton btnBack = new JButton("Back");
        buttonPanel.add(btnBack);
        btnBack.addActionListener(e -> {
            StopCaptureThread();
            m_dlgParent.setVisible(false);
        });

        JButton btnCapture = new JButton("Capture Fingerprint");
        buttonPanel.add(btnCapture);
        btnCapture.addActionListener(e -> captureFingerPrint());

        add(buttonPanel);
    }

    private void StartCaptureThread() {
        m_capture = new CaptureThread(m_reader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT);
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

    private void captureFingerPrint() {
        try {
            // Ensure reader is open when capture starts
            if (m_reader == null) {
                System.out.println("⚠️ No fingerprint reader detected!");
                return;
            }

            try {
                m_reader.Open(Reader.Priority.COOPERATIVE);
                System.out.println("✅ Reader opened successfully.");
            } catch (UareUException e) {
                System.out.println("❌ Failed to open reader: " + e.getMessage());
                return;
            }

            // Show dialog prompt
            fingerDialogue = new JDialog();
            fingerDialogue.setTitle("Fingerprint Capture");
            fingerDialogue.setSize(220, 90);
            fingerDialogue.setLocationRelativeTo(null);
            fingerDialogue.setModal(false);
            fingerDialogue.add(new JLabel("Place Finger on Scanner", SwingConstants.CENTER));
            fingerDialogue.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            fingerDialogue.setAlwaysOnTop(true);
            fingerDialogue.setVisible(true);

            // Start capture thread now
            m_capture = new CaptureThread(m_reader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT);
            m_capture.start(evt -> {
                CaptureThread.CaptureEvent captureEvt = (CaptureThread.CaptureEvent) evt;

                if (captureEvt.capture_result != null) {
                    System.out.println("🧩 Capture quality: " + captureEvt.capture_result.quality);

                    if (captureEvt.capture_result.quality == Reader.CaptureQuality.GOOD) {
                        AdminDatabaseLogic.saveAdmin(
                                conn,
                                captureEvt.capture_result.image.getData(),
                                nameField.getText(),
                                surnameField.getText(),
                                idField.getText()
                        );

                        fingerDialogue.dispose();
                        System.out.println("✅ Fingerprint added to database!");

                        StopCaptureThread();

                        try {
                            m_reader.Close();
                            System.out.println("🔒 Reader closed.");
                        } catch (UareUException ignored) {
                        }

                    } else {
                        System.out.println("❌ No valid capture. Try again...");
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doModal(JDialog dlgParent) {
        if (dlgParent == null) {
            dlgParent = new JDialog((JFrame) null, "Enrollment", true);
        }

        m_dlgParent = dlgParent;

        // Reader will NOT open here — only when capture button is pressed
        m_dlgParent.setContentPane(this);
        m_dlgParent.pack();
        m_dlgParent.setLocationRelativeTo(null);
        m_dlgParent.toFront();
        m_dlgParent.setVisible(true);

        WaitForCaptureThread();
    }

    public static void Run(Reader reader) {
        JDialog dlg = new JDialog((JDialog) null, "Admin Enrollment", true);
        AddAdmin gui = new AddAdmin(reader);
        gui.doModal(dlg);
    }

    /*public static void main(String[] args) {
        try {
            // ✅ Connect to database immediately at startup
            conn = AdminDatabaseConnectivity.getConnection();
            if (conn != null) {
                System.out.println("✅ Database connection established successfully!");
            } else {
                System.out.println("❌ Failed to connect to database!");
            }

            // ✅ Initialize fingerprint reader collection
            ReaderCollection collection = UareUGlobal.GetReaderCollection();
            collection.GetReaders();

            if (collection.size() > 0) {
                Reader reader = collection.get(0);
                System.out.println("🖐 Using reader: " + reader.GetDescription().name);
                AddAdmin.Run(reader);
            } else {
                System.out.println("❌ No fingerprint readers found.");
            }

            UareUGlobal.DestroyReaderCollection();

        } catch (UareUException e) {
            e.printStackTrace();
        }
    }*/

    @Override
    public void actionPerformed(ActionEvent e) {
        // Not used, kept for interface compliance
    }
}
