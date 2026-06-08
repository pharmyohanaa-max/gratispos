package hIMs.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class PharmacyInfoUtil {

    private static final String CONFIG_FILE = "pharmacy.properties";
    private static final String PHARMACY_NAME_KEY = "pharmacyName";

    /**
     * Saves the pharmacy name to a properties file.
     * @param name The pharmacy name to save.
     */
    public static void savePharmacyName(String name) {
        Properties props = new Properties();

        // Load existing properties if file exists
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Set the pharmacy name property
        props.setProperty(PHARMACY_NAME_KEY, name);

        // Save properties back to file
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "Pharmacy Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the pharmacy name from the properties file.
     * @return The saved pharmacy name, or empty string if not set.
     */
    public static String getPharmacyName() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            return "";
        }

        try (FileInputStream in = new FileInputStream(configFile)) {
            props.load(in);
            return props.getProperty(PHARMACY_NAME_KEY, "");
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
}
