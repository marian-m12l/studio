package studio.core.v1.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class AudioConversionTest {
    private String zipName = "SimplifiedSamplePack-60f84e3d-8a37-4b4a-9e67-fc13daad9bb9-v1.zip";

    private static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    @Test
    void convertOgg() throws Exception {
        Path zipPath = classpathResource(zipName);

        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, ClassLoader.getSystemClassLoader())) {
            Path ogg = zipFs.getPath("assets/1a23e1732632e8bbcb7607a92edd3c3ec3c3357a.ogg");

            long oggSize = Files.size(ogg);
            assertEquals(16330, oggSize, "asset ogg");

            System.out.println("Read " + ogg);
            byte[] oggBytes = Files.readAllBytes(ogg);
            assertEquals(16330, oggBytes.length, "old.ogg");

            System.out.println("Write wav");
            byte[] wavBytes = AudioConversion.anyToWave(oggBytes);
            long newWavSize = Files.size(Files.write(zipPath.resolveSibling("new.wav"), wavBytes));
            assertEquals(90938, newWavSize, "new.wav");

            System.out.println("Write ogg");
            byte[] oggBytes2 = AudioConversion.waveToOgg(wavBytes);
            long newOggSize = Files.size(Files.write(zipPath.resolveSibling("new.ogg"), oggBytes2));
            assertEquals(17059, newOggSize, "new.ogg");

            // oldOggSize != newOggSize ???
            // assertArrayEquals(oggBytes, oggBytes2, "Ogg different");
        }
    }
}
