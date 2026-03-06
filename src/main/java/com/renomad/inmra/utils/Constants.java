package com.renomad.inmra.utils;

import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.TimeUtils;

import java.io.FileInputStream;
import java.util.Objects;
import java.util.Properties;

public class Constants {

    private final Properties properties;

    public Constants() {
        this(null);
    }

    public Constants(Properties props) {
        properties = Objects.requireNonNullElseGet(props, Constants::getConfiguredProperties);
        TEMPLATE_DIRECTORY = properties.getProperty("TEMPLATE_DIRECTORY", "templates/");
        PRIVACY_PASSWORD = properties.getProperty("PRIVACY_PASSWORD");
        PRIVACY_COOKIE_MAX_AGE = getProp("PRIVACY_COOKIE_MAX_AGE", 60 * 60 * 24 * 3); // three days
        REGISTER_PREHANDLER = getProp("REGISTER_PREHANDLER", false);
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
            System.out.println("""

                    ********************

                    failed to successfully read memoria.config: using built-in defaults

                    ***************************


                    """);
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
     * The password to be used for viewing private information of living people
     */
    public final String PRIVACY_PASSWORD;

    /**
     * The length of time, in seconds, the privacy cookie will last
     */
    public final int PRIVACY_COOKIE_MAX_AGE;

    /**
     * Whether we will configure a prehandler.  See its definition in
     * the TheRegister class.
     */
    public final boolean REGISTER_PREHANDLER;

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from memoria.config
     */
    private int getProp(String propName, int propDefault) {
        return Integer.parseInt(properties.getProperty(propName, String.valueOf(propDefault)));
    }

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from minum.config
     */
    private boolean getProp(String propName, boolean propDefault) {
        return Boolean.parseBoolean(properties.getProperty(propName, String.valueOf(propDefault)));
    }
}
