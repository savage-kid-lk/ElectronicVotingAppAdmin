package adminApp;



import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import com.digitalpersona.uareu.*;

public class CaptureThread extends Thread {

    public static final String ACT_CAPTURE = "capture_thread_captured";

    public class CaptureEvent extends ActionEvent {
        private static final long serialVersionUID = 101;
        public Reader.CaptureResult capture_result;
        public Reader.Status reader_status;
        public UareUException exception;

        public CaptureEvent(Object source, String action, Reader.CaptureResult cr, Reader.Status st, UareUException ex) {
            super(source, ActionEvent.ACTION_PERFORMED, action);
            capture_result = cr;
            reader_status = st;
            exception = ex;
        }
    }

    private ActionListener m_listener;
    private boolean m_bCancel;
    private Reader m_reader;
    private boolean m_bStream;
    private Fid.Format m_format;
    private Reader.ImageProcessing m_proc;
    private CaptureEvent m_last_capture;

    public CaptureThread(Reader reader, boolean bStream, Fid.Format img_format, Reader.ImageProcessing img_proc) {
        m_bCancel = false;
        m_reader = reader;
        m_bStream = bStream;
        m_format = img_format;
        m_proc = img_proc;
    }

    public void start(ActionListener listener) {
        m_listener = listener;
        super.start();
    }

    public void join(int milliseconds) {
        try {
            super.join(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public CaptureEvent getLastCaptureEvent() {
        return m_last_capture;
    }

    private void Capture() {
        try {
            boolean bReady = false;
            while (!bReady && !m_bCancel) {
                Reader.Status rs = m_reader.GetStatus();
                if (Reader.ReaderStatus.BUSY == rs.status) {
                    Thread.sleep(100);
                } else if (Reader.ReaderStatus.READY == rs.status || Reader.ReaderStatus.NEED_CALIBRATION == rs.status) {
                    bReady = true;
                    break;
                } else {
                    NotifyListener(ACT_CAPTURE, null, rs, null);
                    break;
                }
            }

            if (m_bCancel) {
                Reader.CaptureResult cr = new Reader.CaptureResult();
                cr.quality = Reader.CaptureQuality.CANCELED;
                NotifyListener(ACT_CAPTURE, cr, null, null);
            }

            if (bReady) {
                Reader.CaptureResult cr = m_reader.Capture(m_format, m_proc, m_reader.GetCapabilities().resolutions[0], -1);
                NotifyListener(ACT_CAPTURE, cr, null, null);
            }
        } catch (UareUException e) {
            NotifyListener(ACT_CAPTURE, null, null, e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void NotifyListener(String action, Reader.CaptureResult cr, Reader.Status st, UareUException ex) {
        final CaptureEvent evt = new CaptureEvent(this, action, cr, st, ex);
        m_last_capture = evt;
        if (m_listener != null && action != null && !action.equals("")) {
            javax.swing.SwingUtilities.invokeLater(() -> m_listener.actionPerformed(evt));
        }
    }

    public void cancel() {
        m_bCancel = true;
        try {
            if (!m_bStream) {
                m_reader.CancelCapture();
            }
        } catch (UareUException e) {
        }
    }

    public void run() {
        Capture();
    }
}
