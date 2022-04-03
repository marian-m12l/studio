package studio.config;

import static com.github.stefanbirkner.systemlambda.SystemLambda.restoreSystemProperties;
import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
        String act2 = withEnvironmentVariable(sc.name(), exp2).execute(() -> sc.getValue());
        assertEquals(exp2, act2, "bad env value");

        // from system
        restoreSystemProperties(() -> {
            String exp3 = "fromSys";
            System.setProperty(sc.getPropertyName(), exp3);
            String act3 = sc.getValue();
            assertEquals(exp3, act3, "bad system value");
            // both sys and env : sys wins
            String actBoth = withEnvironmentVariable(sc.name(), exp2).execute(() -> sc.getValue());
            assertEquals(exp3, actBoth, "sys should override env");
        });
    }

}
