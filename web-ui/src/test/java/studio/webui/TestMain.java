package studio.webui;

import java.util.Optional;

import io.vertx.core.Launcher;

public class TestMain {

    public static void main(String... args) {
        // copy from env vars
        Optional.ofNullable(System.getenv("STUDIO_LIBRARY")).ifPresent(s -> System.setProperty("studio.library", s));
        Optional.ofNullable(System.getenv("STUDIO_TMPDIR")).ifPresent(s -> System.setProperty("studio.tmpdir", s));

        // common config
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("vertx.disableDnsResolver", "true");
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.Log4j2LogDelegateFactory");

        // test mode
        System.setProperty("env", "dev");
        System.setProperty("studio.open", "false");

        // real main
        Launcher.executeCommand("run", "studio.webui.MainVerticle");
    }

}
