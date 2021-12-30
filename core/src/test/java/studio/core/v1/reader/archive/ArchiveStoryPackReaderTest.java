package studio.core.v1.reader.archive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.writer.archive.ArchiveStoryPackWriter;

public class ArchiveStoryPackReaderTest {

    private ArchiveStoryPackReader reader = new ArchiveStoryPackReader();
    private ArchiveStoryPackWriter writer = new ArchiveStoryPackWriter();

    private String zipName = "SimplifiedSamplePack-60f84e3d-8a37-4b4a-9e67-fc13daad9bb9-v1.zip";

    private static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    @Test
    void readMetadata() throws IOException, URISyntaxException {
        Path zipPath = classpathResource(zipName);
        System.out.println("zipPath: " + zipPath);

        // v2
        long t1 = System.currentTimeMillis();
        StoryPackMetadata meta2 = reader.readMetadata(zipPath);
        long t2 = System.currentTimeMillis();

        System.out.println("readMetadata:");
        System.out.printf("v2 (%s ms) : %s\n", t2 - t1, meta2);

//        assertEquals(meta1.getUuid(), meta2.getUuid());
//        assertEquals(meta1.getTitle(), meta2.getTitle());
//        assertEquals(meta1.getVersion(), meta2.getVersion());
//        assertEquals(meta1.getDescription(), meta2.getDescription());
//        assertEquals(meta1.getSectorSize(), meta2.getSectorSize());
//        assertEquals(meta1.getFormat(), meta2.getFormat());
//        assertArrayEquals(meta1.getThumbnail(), meta2.getThumbnail());
    }

    @Test
    void readWriteStoryPack() throws IOException, URISyntaxException {
        Path zipPath = classpathResource(zipName);
        System.out.println("zipPath: " + zipPath);

        // v1
        long t1 = System.currentTimeMillis();
        StoryPack sp1;
        try (InputStream is = Files.newInputStream(zipPath)) {
            sp1 = reader.read(is);
        }
        long t2 = System.currentTimeMillis();
        // v2
        StoryPack sp2 = reader.read(zipPath);
        long t3 = System.currentTimeMillis();

        System.out.println("readStoryPack:");
        System.out.printf("v1 (%s ms) : %s\n", t2 - t1, sp1);
        System.out.printf("v2 (%s ms) : %s\n", t3 - t2, sp2);

        assertAll("StoryPack", //
                () -> assertEquals(sp1.getUuid(), sp2.getUuid()),
                () -> assertEquals(sp1.getVersion(), sp2.getVersion()),
                () -> assertEquals(sp1.getEnriched().getTitle(), sp2.getEnriched().getTitle()),
                () -> assertEquals(sp1.getEnriched().getDescription(), sp2.getEnriched().getDescription()),
                () -> assertEquals(sp1.getStageNodes().size(), sp2.getStageNodes().size()) //
        );

        for (int i = 0; i < sp1.getStageNodes().size(); i++) {
            StageNode sn1 = sp1.getStageNodes().get(i);
            StageNode sn2 = sp2.getStageNodes().get(i);
            assertAll("StageNode-" + i, //
                    () -> assertEquals(sn1.getUuid(), sn2.getUuid()));
            EnrichedNodeMetadata en1 = sn1.getEnriched();
            EnrichedNodeMetadata en2 = sn2.getEnriched();
            if (en1 != null && en2 != null) {
                assertAll("StageNode-" + i + "-Enriched", //
                        () -> assertEquals(en1.getType(), en2.getType(), "type"),
                        () -> assertEquals(en1.getGroupId(), en2.getGroupId(), "groupid"),
                        () -> assertEquals(en1.getName(), en2.getName(), "name")
//                        () -> assertEquals(en1.getPosition().getX(), en2.getPosition().getX(), "x"),
//                        () -> assertEquals(en1.getPosition().getY(), en2.getPosition().getY(), "y") //
                );
            }
        }

        // ------------------

        // v1
        long tt1 = System.currentTimeMillis();
        try (OutputStream is = Files.newOutputStream(zipPath.resolveSibling("output-v1.zip"))) {
            writer.write(sp1, is);
        }
        long tt2 = System.currentTimeMillis();
        // v2
        writer.write(sp1, zipPath.resolveSibling("output-v2.zip"));
        long tt3 = System.currentTimeMillis();

        System.out.println("writeStoryPack:");
        System.out.printf("v1 (%s ms)\n", tt2 - tt1);
        System.out.printf("v2 (%s ms)\n", tt3 - tt2);
    }
}
