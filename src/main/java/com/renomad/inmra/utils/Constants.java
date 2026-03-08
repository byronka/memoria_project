package com.renomad.inmra.utils;

import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.utils.TimeUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        DO_NEW_PASSWORD_COUNTDOWN = getProp("DO_NEW_PASSWORD_COUNTDOWN", true);
    }

    /**
     * This overload allows you to specify that the contents of the
     * properties file should be shown when it's read.
     */
    public static Properties getConfiguredProperties() {
        var properties = new Properties();
        String fileName = "memoria.config";
        Path memoriaConfigPath = Path.of(fileName);
        if (!Files.exists(memoriaConfigPath)) {
            showWarningAndCreateDefaultConfig(memoriaConfigPath);
        }
        try (FileInputStream fis = new FileInputStream(fileName)) {

            System.out.println(TimeUtils.getTimestampIsoInstant() +
                    " found properties file at ./memoria.config.  Loading properties");
            properties.load(fis);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return properties;
    }

    /**
     * Shows the user a warning that we can't find their
     * memoria.config file, and we're going to make them one.
     */
    private static void showWarningAndCreateDefaultConfig(Path memoriaConfigPath) {
        String privacyPassword = StringUtils.generateSecureRandomString(6);
        System.out.printf("""
                
                ***************************
                
                Failed to find memoria.config.  Adding one to this directory
                with contents as follows.  Please change the password to something unique
                and easily remembered - this will be the shared password for your family
                members to use when accessing the site, it does not provide administrative
                privileges so it is not a critical security risk.  Still, to avoid private
                information being publicly available, this is necessary.
                
                PRIVACY_PASSWORD=%s
                
                Again, this will be written to your current directory, at memoria.config. The
                value is applied when the server starts.
                
                ***************************
                
                %n""", privacyPassword);
        MyThread.sleep(1000);
        System.out.print("Continuing in 20...");
        MyThread.sleep(1000);
        for (int i = 19; i > 0; i--) {
            System.out.print(i + "...");
            MyThread.sleep(1000);
        }
        try {
            Files.writeString(memoriaConfigPath, "PRIVACY_PASSWORD=" + privacyPassword);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
     * Whether to run a 10-second countdown in the logs when there is
     * no initial admin password.
     */
    public final boolean DO_NEW_PASSWORD_COUNTDOWN;

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
