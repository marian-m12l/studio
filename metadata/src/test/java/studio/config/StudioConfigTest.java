package studio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

class StudioConfigTest {

    @Test
    void getPropertyName() throws Exception {
        String exp = "studio.dev.mode";
        String act = StudioConfig.STUDIO_DEV_MODE.getPropertyName();
        assertEquals(exp, act, "bad property name");
    }

    @Test
    void getValue() throws Exception {
        StudioConfig sc = StudioConfig.STUDIO_DEV_MODE;

        // none -> default value
        String exp1 = sc.getDefaultValue();
        String act1 = sc.getValue();
        assertEquals(exp1, act1, "bad default value");

        // from env
        String exp2 = "fromEnv";
        EnvironmentVariables env = new EnvironmentVariables(sc.name(), exp2);
        String act2 = env.execute(() -> sc.getValue());
        assertEquals(exp2, act2, "bad env value");

        // from system
        String exp3 = "fromSys";
        SystemProperties sys = new SystemProperties(sc.getPropertyName(), exp3);
        sys.execute(() -> {
            String act3 = sc.getValue();
            assertEquals(exp3, act3, "bad system value");
            // both sys and env : sys wins
            String actBoth = env.execute(() -> sc.getValue());
            assertEquals(exp3, actBoth, "sys should override env");
        });
    }

}
