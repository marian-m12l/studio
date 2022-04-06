package studio.core.v1.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AudioConversionTest {

    private String zipName = "SimplifiedSamplePack-60f84e3d-8a37-4b4a-9e67-fc13daad9bb9-v1.zip";

    private static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    @Test
    void convertOgg() throws Exception {
        Path zipPath = classpathResource(zipName);

        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, ClassLoader.getSystemClassLoader())) {
            Path insideOgg = zipFs.getPath("assets/1a23e1732632e8bbcb7607a92edd3c3ec3c3357a.ogg");

            long insideOggSize = Files.size(insideOgg);
            assertEquals(16330, insideOggSize, "asset ogg");

            System.out.println("Read " + insideOgg);
            byte[] insideOggBytes = Files.readAllBytes(insideOgg);
            assertEquals(16330, insideOggBytes.length, "old.ogg");

            System.out.println("Write wav");
            byte[] outsideWavBytes = AudioConversion.anyToWave(insideOggBytes);
            Path outsideWav = Files.write(zipPath.resolveSibling("new.wav"), outsideWavBytes);
            long outsideWavSize = Files.size(outsideWav);
            assertEquals(90938, outsideWavSize, "new.wav");

            System.out.println("Write ogg");
            byte[] outsideOggBytes = AudioConversion.waveToOgg(outsideWavBytes);
            Path outsideOgg = Files.write(zipPath.resolveSibling("new.ogg"), outsideOggBytes);
            long outsideOggSize = Files.size(outsideOgg);
            assertEquals(17059, outsideOggSize, "new.ogg");

            // oldOggSize != newOggSize ???
            // assertArrayEquals(oggBytes, oggBytes2, "Ogg different");
        }
    }

    @Test
    void convertOggToMp3() throws Exception {
        Path zipPath = classpathResource(zipName);
        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, ClassLoader.getSystemClassLoader())) {
            Path insideOgg = zipFs.getPath("assets/1a23e1732632e8bbcb7607a92edd3c3ec3c3357a.ogg");

            System.out.println("Read " + insideOgg);
            byte[] insideOggBytes = Files.readAllBytes(insideOgg);
            assertEquals(16330, insideOggBytes.length, "old.ogg");

            System.out.println("Write mp3");
            byte[] outsideMp3Bytes = AudioConversion.anyToMp3(insideOggBytes);
            Path outsideMp3 = Files.write(zipPath.resolveSibling("new.mp3"), outsideMp3Bytes);
            long outsideMp3Size = Files.size(outsideMp3);
            assertEquals(15247, outsideMp3Size, "new.mp3");
        }
    }
}
