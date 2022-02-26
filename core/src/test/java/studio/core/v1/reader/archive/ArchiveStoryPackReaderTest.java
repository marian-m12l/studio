package studio.core.v1.reader.archive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.enriched.EnrichedPackMetadata;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.StoryPackReader;
import studio.core.v1.utils.PackFormat;
import studio.core.v1.writer.StoryPackWriter;

class ArchiveStoryPackReaderTest {

    private StoryPackReader reader = PackFormat.ARCHIVE.getReader();
    private StoryPackWriter writer = PackFormat.ARCHIVE.getWriter();

    private String zipName = "SimplifiedSamplePack-60f84e3d-8a37-4b4a-9e67-fc13daad9bb9-v1.zip";

    private static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    @Test
    void readMetadata() throws IOException, URISyntaxException {
        Path zipPath = classpathResource(zipName);
        System.out.println("zipPath: " + zipPath);

        long t1 = System.currentTimeMillis();
        StoryPackMetadata metaActual = reader.readMetadata(zipPath);
        long t2 = System.currentTimeMillis();

        System.out.printf("readMetadata (%s ms) : %s\n", t2 - t1, metaActual);

        StoryPackMetadata metaExpected = new StoryPackMetadata(PackFormat.ARCHIVE);
        metaExpected.setVersion((short) 1);
        metaExpected.setTitle("SimplifiedSamplePack");
        metaExpected.setUuid("60f84e3d-8a37-4b4a-9e67-fc13daad9bb9");
        metaExpected.setDescription("");
        metaExpected.setSectorSize(null);
        metaExpected.setThumbnail(null);

        assertAll("StoryPackMetadata", //
                () -> assertEquals(metaExpected.getUuid(), metaActual.getUuid()),
                () -> assertEquals(metaExpected.getTitle(), metaActual.getTitle()),
                () -> assertEquals(metaExpected.getVersion(), metaActual.getVersion()),
                () -> assertEquals(metaExpected.getDescription(), metaActual.getDescription()),
                () -> assertEquals(metaExpected.getSectorSize(), metaActual.getSectorSize()),
                () -> assertEquals(metaExpected.getFormat(), metaActual.getFormat()),
                () -> assertArrayEquals(metaExpected.getThumbnail(), metaActual.getThumbnail()));
    }

    @Test
    void readWriteStoryPack() throws Exception {
        Path zipPath = classpathResource(zipName);
        System.out.println("zipPath: " + zipPath);

        long t1 = System.currentTimeMillis();
        StoryPack spActual = reader.read(zipPath);
        long t2 = System.currentTimeMillis();

        System.out.printf("readStoryPack (%s ms) : %s\n", t2 - t1, spActual);

        StoryPack spExpected = new StoryPack();
        spExpected.setVersion((short) 1);
        spExpected.setFactoryDisabled(false);
        spExpected.setNightModeAvailable(false);
        spExpected.setUuid("60f84e3d-8a37-4b4a-9e67-fc13daad9bb9");
        spExpected.setEnriched(new EnrichedPackMetadata("SimplifiedSamplePack", ""));

        List<StageNode> stages = Arrays.asList( //
                new StageNode("60f84e3d-8a37-4b4a-9e67-fc13daad9bb9", null, null, null, null,
                        new ControlSettings(false, false, false, false, false), //
                        new EnrichedNodeMetadata("Pack selection stage", EnrichedNodeType.COVER, null, null)), //
                new StageNode("0607ef7d-89db-4b09-9e7f-077d0c8c5690", null, null, null, null, //
                        new ControlSettings(false, false, false, false, false), //
                        new EnrichedNodeMetadata("Story: Alice + Jungle", EnrichedNodeType.STORY,
                                "0607ef7d-89db-4b09-9e7f-077d0c8c5690", null)), //
                new StageNode("c4aebf59-e53f-443e-8e52-cf1872181784", null, null, null, null, //
                        new ControlSettings(false, false, false, false, false), //
                        new EnrichedNodeMetadata("Story: Alice + City", EnrichedNodeType.STORY,
                                "c4aebf59-e53f-443e-8e52-cf1872181784", null)), //
                new StageNode("b6f86bdd-30ee-4ef7-a50d-5d9bab6c28e1", null, null, null, null, //
                        new ControlSettings(false, false, false, false, false), //
                        new EnrichedNodeMetadata("Story: Bob + Jungle", EnrichedNodeType.STORY,
                                "b6f86bdd-30ee-4ef7-a50d-5d9bab6c28e1", null)), //
                new StageNode("1ed3b560-7ec0-4b4a-8f63-555b508b2186", null, null, null, null, //
                        new ControlSettings(false, false, false, false, false), //
                        new EnrichedNodeMetadata("Story: Bob + Desert", EnrichedNodeType.STORY,
                                "1ed3b560-7ec0-4b4a-8f63-555b508b2186", null))//
        // etc...
        );
        spExpected.setStageNodes(stages);

        assertAll("StoryPack", //
                () -> assertEquals(spExpected.getUuid(), spActual.getUuid()),
                () -> assertEquals(spExpected.getVersion(), spActual.getVersion()),
                () -> assertEquals(spExpected.getEnriched().getTitle(), spActual.getEnriched().getTitle()),
                () -> assertEquals(spExpected.getEnriched().getDescription(), spActual.getEnriched().getDescription())
        // () -> assertEquals(spExpected.getStageNodes().size(),
        // spActual.getStageNodes().size()) //
        );

        spActual.getStageNodes().forEach(s -> {
            System.out.printf("%s %s %s %s \n", s.getUuid(), s.getEnriched().getType(), s.getEnriched().getGroupId(),
                    s.getEnriched().getName());
        });

        for (int i = 0; i < spExpected.getStageNodes().size(); i++) {
            StageNode sn1 = spExpected.getStageNodes().get(i);
            StageNode sn2 = spActual.getStageNodes().get(i);
            assertAll("StageNode-" + i, //
                    () -> assertEquals(sn1.getUuid(), sn2.getUuid()));
            EnrichedNodeMetadata en1 = sn1.getEnriched();
            EnrichedNodeMetadata en2 = sn2.getEnriched();
            if (en1 != null && en2 != null) {
                assertAll("StageNode-" + i + "-Enriched", //
                        () -> assertEquals(en1.getType(), en2.getType(), "type"),
                        () -> assertEquals(en1.getGroupId(), en2.getGroupId(), "groupid"),
                        () -> assertEquals(en1.getName(), en2.getName(), "name"));
            }
        }

        // write
        long tt1 = System.currentTimeMillis();
        Path zip = zipPath.resolveSibling("output-from-zip.zip");
        writer.write(spActual, zip, true);
        long tt2 = System.currentTimeMillis();

        System.out.printf("writeStoryPack (%s ms)\n", tt2 - tt1);
    }

}
