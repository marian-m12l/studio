package studio.core.v1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.asset.AudioAsset;
import studio.core.v1.model.asset.AudioType;
import studio.core.v1.model.asset.ImageAsset;
import studio.core.v1.model.asset.ImageType;
import studio.core.v1.utils.io.FileUtils;

class StoryPackConverterTest {

    static Path studioHomePath = Path.of("./target/studio");
    static Path libraryPath = studioHomePath.resolve("library");
    static Path tmpPath = studioHomePath.resolve("tmp");

    static final String TEST_PACK_NAME = "SimplifiedSamplePack.zip";

    static StoryPackConverter storyPackConverter = new StoryPackConverter(libraryPath, tmpPath);

    @BeforeAll
    static void beforeAll() throws IOException, URISyntaxException {
        // recreate dirs
        FileUtils.emptyDirectory(libraryPath);
        FileUtils.emptyDirectory(tmpPath);
        // add 1 test pack
        Path testPackSource = classpathResource(TEST_PACK_NAME);
        Path testPackLibrary = libraryPath.resolve(TEST_PACK_NAME);
        Files.copy(testPackSource, testPackLibrary, StandardCopyOption.REPLACE_EXISTING);
    }

    static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    @Test
    void hasCompressedAssets() {
        StoryPack sp = new StoryPack();
        sp.setStageNodes(Arrays.asList( //
                new StageNode("60f84e3d-8a37-4b4a-9e67-fc13daad9bb9", null, null, null, null, null, null), //
                new StageNode("10f84e3d-8as7-5b4a-6e67-4968daad9bb9", null, null, null, null, null, null), //
                new StageNode("0607ef7d-89db-4b09-9e7f-077d0c8c5690", null, null, null, null, null, null) //
        ));
        StageNode sn = sp.getStageNodes().get(1);
        // 1) KO
        boolean expected = false;
        // No asset
        assertEquals(expected, StoryPackConverter.hasCompressedAssets(sp));
        // WAV
        sn.setAudio(new AudioAsset(AudioType.WAV, null));
        assertEquals(expected, StoryPackConverter.hasCompressedAssets(sp));
        // WAV + BMP
        sn.setImage(new ImageAsset(ImageType.BMP, null));
        assertEquals(expected, StoryPackConverter.hasCompressedAssets(sp));
        // BMP
        sn.setAudio(null);
        assertEquals(expected, StoryPackConverter.hasCompressedAssets(sp));
        // 2) OK
        expected = true;
        // JPG
        sn.setImage(new ImageAsset(ImageType.JPEG, null));
        assertEquals(expected, StoryPackConverter.hasCompressedAssets(sp));
        // JPG + MP3
        sn.setAudio(new AudioAsset(AudioType.MP3, null));
        assertEquals(expected, StoryPackConverter.hasCompressedAssets(sp));
        // MP3
        sn.setImage(null);
        assertEquals(expected, StoryPackConverter.hasCompressedAssets(sp));
    }

    @Test
    void convertZip() {
        // convert zip -> zip
        StoryTellerException e = assertThrows(StoryTellerException.class, () -> {
            storyPackConverter.convert(TEST_PACK_NAME, PackFormat.ARCHIVE, true);
        });
        assertEquals("Pack is already in " + PackFormat.ARCHIVE + " format : " + TEST_PACK_NAME, e.getMessage(),
                "Invalid errorMessage");
    }

    @Test
    void convertRaw() {
        // convert zip -> raw
        Path newRaw = storyPackConverter.convert(TEST_PACK_NAME, PackFormat.RAW, true);
        assertEquals(PackFormat.RAW, PackFormat.fromPath(newRaw), "Pack " + newRaw.getFileName());
        // convert raw -> zip
        Path newZipPath = storyPackConverter.convert(newRaw.getFileName().toString(), PackFormat.ARCHIVE, true);
        assertEquals(PackFormat.ARCHIVE, PackFormat.fromPath(newZipPath), "Pack " + newZipPath.getFileName());
    }

    @Test
    void convertFs() {
        // convert zip -> fs
        Path newFs = storyPackConverter.convert(TEST_PACK_NAME, PackFormat.FS, true);
        assertEquals(PackFormat.FS, PackFormat.fromPath(newFs), "Pack " + newFs.getFileName());
        // convert fs -> zip
        Path newZipPath = storyPackConverter.convert(newFs.getFileName().toString(), PackFormat.ARCHIVE, true);
        assertEquals(PackFormat.ARCHIVE, PackFormat.fromPath(newZipPath), "Pack " + newZipPath.getFileName());
    }

}
