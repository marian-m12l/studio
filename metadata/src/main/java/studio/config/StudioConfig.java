package studio.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum StudioConfig {

    // auto open browser (studio.open.browser)
    STUDIO_OPEN_BROWSER("true"),
    // official json database (studio.db.official)
    STUDIO_DB_OFFICIAL(home() + "/.studio/db/official.json"),
    // unofficial json database (studio.db.unofficial)
    STUDIO_DB_UNOFFICIAL(home() + "/.studio/db/unofficial.json"),
    // library dir (studio.library)
    STUDIO_LIBRARY(home() + "/.studio/library/"),
    // temp dir (studio.tmpdir)
    STUDIO_TMPDIR(home() + "/.studio/tmp/"),
    // [test] mock device (studio.mock.device)
    STUDIO_MOCK_DEVICE(home() + "/.studio/device/"),
    // [test] dev mode (studio.dev.mode)
    STUDIO_DEV_MODE("prod");

    private static final Logger LOGGER = LogManager.getLogger(StudioConfig.class);

    private String defaultValue;

    private StudioConfig(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    private static String home() {
        return System.getProperty("user.home");
    }

    public String getPropertyName() {
        return name().replace('_', '.').toLowerCase();
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Override property value.
     * 
     * @param value
     */
    public void set(String value) {
        System.setProperty(getPropertyName(), value);
    }

    /**
     * Return system property, then env variable and finally default value.
     * 
     * @return
     */
    public String getValue() {
        // search java system variable
        String systemKey = getPropertyName();
        String sysValue = System.getProperty(systemKey);
        LOGGER.trace("sys {}={}", systemKey, sysValue);
        if (sysValue != null) {
            return sysValue;
        }
        // search env variable
        String envValue = System.getenv(name());
        LOGGER.trace("env {}={}", name(), envValue);
        if (envValue != null) {
            return envValue;
        }
        // default
        LOGGER.trace("def {}={}", name(), defaultValue);
        return defaultValue;
    }

}
