package studio.webui;

import java.util.Optional;

import io.vertx.core.Launcher;
import studio.metadata.DatabaseMetadataService;
import studio.webui.service.LibraryService;

public class TestMain {

    /** Copy env vars to system vars. */
    private static void envToSystemProperty(String a, String b) {
        Optional.ofNullable(System.getenv(a)).ifPresent(s -> System.setProperty(b, s));
    }

    public static void main(String... args) {
        // copy from env vars
        envToSystemProperty("STUDIO_LIBRARY", LibraryService.LOCAL_LIBRARY_PROP);
        envToSystemProperty("STUDIO_TMPDIR", LibraryService.TMP_DIR_PROP);
        envToSystemProperty("STUDIO_DB_OFFICIAL", DatabaseMetadataService.OFFICIAL_DB_PROP);
        envToSystemProperty("STUDIO_DB_UNOFFICIAL", DatabaseMetadataService.UNOFFICIAL_DB_PROP);

        // common config
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("vertx.disableDnsResolver", "true");
        // System.setProperty("java.util.logging.manager",
        // "org.apache.logging.log4j.jul.LogManager");
        // System.setProperty("vertx.logger-delegate-factory-class-name",
        // "io.vertx.core.logging.Log4j2LogDelegateFactory");

        // test mode
        if (args.length > 0 && "dev".equals(args[0])) {
            System.setProperty("env", "dev");
            System.setProperty("studio.open", "false");
        }

        // real main
        Launcher.executeCommand("run", "studio.webui.MainVerticle");
    }

}
