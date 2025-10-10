


import com.digitalpersona.uareu.Reader;
import com.digitalpersona.uareu.ReaderCollection;
import com.digitalpersona.uareu.UareUException;

public class Selection {

    /**
     * Automatically detects and selects the first available fingerprint reader.
     *
     * @param collection The ReaderCollection obtained from UareUGlobal.GetReaderCollection()
     * @return The first Reader found, or null if no readers are detected.
     */
    public static Reader Select(ReaderCollection collection) {
        try {
            // Refresh the list of connected readers
            collection.GetReaders();
            // If readers exist, select the first one
            if (collection.size() > 0) {
                Reader selectedReader = collection.get(0);
                return selectedReader;
            } else {
                System.out.println("No fingerprint readers found.");
                return null;
            }
        } catch (UareUException e) {
            System.err.println("Error retrieving readers: " + e.getMessage());
            return null;
        }
    }
}
