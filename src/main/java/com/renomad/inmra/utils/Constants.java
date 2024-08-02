package com.renomad.inmra.utils;

import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.TimeUtils;

import java.io.FileInputStream;
import java.util.Properties;

public class Constants {

    private final Properties properties;

    public Constants() {
        this(null);
    }

    public Constants(Properties props) {
        if (props == null) {
            properties = getConfiguredProperties();
        } else {
            properties = props;
        }
        TEMPLATE_DIRECTORY = properties.getProperty("TEMPLATE_DIRECTORY", "src/main/webapp/templates/");
        COUNT_OF_PHOTO_CHECKS = getProp("COUNT_OF_PHOTO_CHECKS", 10);
        WAIT_TIME_PER_PHOTO_CHECK = getProp("WAIT_TIME_PER_PHOTO_CHECK", 1000);
    }
    /**
     * This overload allows you to specify that the contents of the
     * properties file should be shown when it's read.
     */
    public static Properties getConfiguredProperties() {
        var properties = new Properties();
        String fileName = "memoria.config";
        try (FileInputStream fis = new FileInputStream(fileName)) {
            System.out.println(TimeUtils.getTimestampIsoInstant() +
                    " found properties file at ./memoria.config.  Loading properties");
            properties.load(fis);
        } catch (Exception ex) {
            System.out.println("\n********************\n\n" +
                    "failed to successfully read memoria.config: using built-in defaults\n\n" +
                    "***************************\n\n\n");
            MyThread.sleep(1000);
            System.out.print("Continuing in 5...");
            MyThread.sleep(1000);
            System.out.print("4...");
            MyThread.sleep(1000);
            System.out.print("3...");
            MyThread.sleep(1000);
            System.out.print("2...");
            MyThread.sleep(1000);
            System.out.print("1...");
            MyThread.sleep(1000);
            System.out.print("\n\n");
            return new Properties();
        }
        return properties;
    }

    /**
     * Where we store HTML templates
     */
    public final String TEMPLATE_DIRECTORY;

    /**
     * Count of times we will check for whether a photo has been converted
     */
    public final int COUNT_OF_PHOTO_CHECKS;

    /**
     * How long we'll wait each check (see {@link #COUNT_OF_PHOTO_CHECKS}
     * for the photo to be converted
     */
    public final int WAIT_TIME_PER_PHOTO_CHECK;

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from memoria.config
     */
    private int getProp(String propName, int propDefault) {
        return Integer.parseInt(properties.getProperty(propName, String.valueOf(propDefault)));
    }
}
