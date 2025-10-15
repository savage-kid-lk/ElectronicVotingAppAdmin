

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

import com.digitalpersona.uareu.*;
import java.awt.FlowLayout;

public class EnrollmentGUI extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static final String ACT_BACK = "back";

    private CaptureThread m_capture;
    private Reader m_reader;
    private JDialog m_dlgParent;
    private JLabel namelJLabel, surnameLabel, idLabel;
    private JTextField nameField, surnameField, idField;
    private JPanel buttonPanel;
    JDialog fingerDialogue;

    private final String m_strPrompt = "Enrollment started\nPut any finger on the reader\n\n";

    public EnrollmentGUI(Reader reader) {
        m_reader = reader;

        BoxLayout layout = new BoxLayout(this, BoxLayout.Y_AXIS);
        setLayout(layout);

        add(Box.createVerticalStrut(5));
        //user information collection
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

        buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

        JButton btnBack = new JButton("Back");
        buttonPanel.add(btnBack);

        btnBack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                StopCaptureThread();
                m_dlgParent.setVisible(false);
            }
        });

        JButton btnCapture = new JButton("Capture Fingerprint");
        buttonPanel.add(btnCapture);

        btnCapture.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                captureFingerPrint();
            }
        });

        add(buttonPanel);
        add(Box.createVerticalStrut(5));
        setOpaque(true);
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

    public void captureFingerPrint() {

        fingerDialogue = new JDialog();
        fingerDialogue.setTitle("Info");
        fingerDialogue.setSize(200, 75);
        fingerDialogue.setLocationRelativeTo(null);
        fingerDialogue.setModal(false); // non-blocking
        fingerDialogue.add(new JLabel("Place Finger on Scanner", SwingConstants.CENTER));
        fingerDialogue.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        fingerDialogue.setAlwaysOnTop(true);
        fingerDialogue.setVisible(true);
        m_capture = new CaptureThread(m_reader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT);

        m_capture.start(evt -> {
            CaptureThread.CaptureEvent captureEvt = (CaptureThread.CaptureEvent) evt;
            if (captureEvt.capture_result != null && captureEvt.capture_result.quality == Reader.CaptureQuality.GOOD) {
                FingerprintDAO.saveFingerprint(captureEvt.capture_result.image.getData(),nameField.getText(), surnameField.getText(), idField.getText());
                fingerDialogue.dispose();
                System.out.println("Fingerprint added to database!\n\n");
                if (m_dlgParent != null) {
                    m_dlgParent.dispose();
                }
                StopCaptureThread();
            } else {
                System.out.println("No valid capture. Try again...\n");
            }
        });
    }

    private void doModal(JDialog dlgParent) {
        try {
            m_reader.Open(Reader.Priority.COOPERATIVE);
        } catch (UareUException e) {
            System.out.println("Error opening reader: " + e.getMessage());
            return;
        }

        StartCaptureThread();

        m_dlgParent = dlgParent;
        m_dlgParent.setContentPane(this);
        m_dlgParent.pack();
        m_dlgParent.setLocationRelativeTo(null);
        m_dlgParent.toFront();
        m_dlgParent.setVisible(true);

        StopCaptureThread();
        WaitForCaptureThread();

        try {
            m_reader.Close();
        } catch (UareUException ignored) {
        }
    }

    public static void Run(Reader reader) {
        JDialog dlg = new JDialog((JDialog) null, "Enrollment", true);
        EnrollmentGUI gui = new EnrollmentGUI(reader);
        gui.doModal(dlg);
    }

    /*public static void main(String[] args) {
        try {
            ReaderCollection collection = UareUGlobal.GetReaderCollection();
            collection.GetReaders();

            if (collection.size() > 0) {
                Reader reader = collection.get(0);
                System.out.println("Using reader: " + reader.GetDescription().name);
                EnrollmentGUI.Run(reader);
            } else {
                System.out.println("No fingerprint readers found.");
            }

            UareUGlobal.DestroyReaderCollection();
        } catch (UareUException e) {
            e.printStackTrace();
        }
    }*/

    @Override
    public void actionPerformed(ActionEvent e) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
