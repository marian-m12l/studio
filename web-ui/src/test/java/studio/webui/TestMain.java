package studio.webui;

import io.vertx.core.Launcher;
import studio.config.StudioConfig;

public class TestMain {

    public static void main(String... args) {
        // common config
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("vertx.disableDnsResolver", "true");

        // test mode
        if (args.length > 0 && "dev".equals(args[0])) {
            System.setProperty(StudioConfig.STUDIO_DEV_MODE.getPropertyName(), "dev");
            System.setProperty(StudioConfig.STUDIO_OPEN_BROWSER.getPropertyName(), "false");
        }

        // real main
        Launcher.executeCommand("run", "studio.webui.MainVerticle");
    }

}
