package adminApp;

import com.digitalpersona.uareu.*;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminVerification {

    private Reader m_reader;
    private CaptureThread m_capture;
    private Connection conn;

    public AdminVerification(Reader reader, Connection conn) {
        this.m_reader = reader;
        this.conn = conn;
    }

    // Callback interface for results
    public interface VerificationCallback {
        void onVerificationComplete(boolean verified, String adminName, String adminSurname);
    }

    public void startVerification(VerificationCallback callback) {
        try {
            m_reader.Open(Reader.Priority.COOPERATIVE);
            startCaptureThread(callback);
            System.out.println("🔍 Waiting for admin fingerprint...");
        } catch (UareUException e) {
            System.err.println("❌ Error opening reader: " + e.getMessage());
            callback.onVerificationComplete(false, null, null);
        }
    }

    private void startCaptureThread(VerificationCallback callback) {
        m_capture = new CaptureThread(
                m_reader,
                false,
                Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT
        );

        m_capture.start(evt -> {
            if (evt.getActionCommand().equals(CaptureThread.ACT_CAPTURE)) {
                CaptureThread.CaptureEvent ce = (CaptureThread.CaptureEvent) evt;
                if (ce.capture_result != null && ce.capture_result.quality == Reader.CaptureQuality.GOOD) {
                    try {
                        processFingerprint(ce.capture_result.image, callback);
                    } catch (SQLException ex) {
                        Logger.getLogger(AdminVerification.class.getName()).log(Level.SEVERE, null, ex);
                        callback.onVerificationComplete(false, null, null);
                    }
                }
            }
        });
    }

    private void stopCaptureThread() {
        if (m_capture != null) {
            try {
                m_capture.cancel();
                m_capture.join();
                System.out.println("🧩 Capture thread stopped.");
            } catch (InterruptedException e) {
                System.err.println("⚠️ Interrupted while stopping capture: " + e.getMessage());
            }
        }

        if (m_reader != null) {
            try {
                m_reader.Close();
                System.out.println("🟢 Reader closed successfully.");
            } catch (UareUException e) {
                System.err.println("⚠️ Failed to close reader: " + e.getMessage());
            }
        }
    }

    private void processFingerprint(Fid fid, VerificationCallback callback) throws SQLException {
        Engine engine = UareUGlobal.GetEngine();

        // Use the shared connection instead of opening a new one
        try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT NAME, SURNAME, ID_NUMBER, FINGERPRINT FROM Admins");
             ResultSet rs = ps.executeQuery()) {

            Fmd capturedFmd = engine.CreateFmd(fid, Fmd.Format.ANSI_378_2004);
            boolean matched = false;
            String adminName = null, adminSurname = null;

            while (rs.next()) {
                byte[] storedData = rs.getBytes("FINGERPRINT");
                if (storedData == null) continue;

                int width = fid.getViews()[0].getWidth();
                int height = fid.getViews()[0].getHeight();
                int resolution = fid.getImageResolution();
                int finger_position = fid.getViews()[0].getFingerPosition();
                int cbeff_id = fid.getCbeffId();

                Fmd storedFmd = engine.CreateFmd(
                        storedData, width, height, resolution, finger_position, cbeff_id, Fmd.Format.ANSI_378_2004);

                int score = engine.Compare(capturedFmd, 0, storedFmd, 0);

                if (score < Engine.PROBABILITY_ONE / 100000) {
                    matched = true;
                    adminName = rs.getString("NAME");
                    adminSurname = rs.getString("SURNAME");
                    System.out.println("✅ Match found for admin: " + adminName + " " + adminSurname);
                    break;
                }
            }

            stopCaptureThread();
            callback.onVerificationComplete(matched, adminName, adminSurname);

        } catch (UareUException e) {
            System.err.println("❌ Error processing fingerprint: " + e.getMessage());
            callback.onVerificationComplete(false, null, null);
        }
    }
}
