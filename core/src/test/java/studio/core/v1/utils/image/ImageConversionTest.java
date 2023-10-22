package studio.core.v1.utils.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ImageConversionTest {

    private String zipName = "SimplifiedSamplePack.zip";

    private static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    @Test
    void convertPngToBmp() throws Exception {
        Path zipPath = classpathResource(zipName);
        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, ClassLoader.getSystemClassLoader())) {
            Path insidePng = zipFs.getPath("assets/4977589ba6e6d131a500309d3f8ee84c66b615f1.png");

            System.out.println("Read " + insidePng);
            byte[] insidePngBytes = Files.readAllBytes(insidePng);
            assertEquals(2156, insidePngBytes.length, "old.png");

            System.out.println("Write Bmp");
            byte[] outsideBmpBytes = ImageConversion.anyToRLECompressedBitmap(insidePngBytes);
            Path outsideBmp = Files.write(zipPath.resolveSibling("new.bmp"), outsideBmpBytes);
            long outsideBmpSize = Files.size(outsideBmp);
            assertEquals(3012, outsideBmpSize, "new.bmp");
        }
    }
}
