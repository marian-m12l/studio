/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.service.archive;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.service.PackFormat;
import studio.core.v1.service.StoryPackReader;
import studio.core.v1.utils.stream.ThrowingConsumer;

public class ArchiveStoryPackReader implements StoryPackReader {

    private ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(Include.NON_NULL);

    public StoryPackMetadata readMetadata(Path zipPath) throws IOException {
        // Zip archive contains a json file and separate assets
        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, ClassLoader.getSystemClassLoader())) {
            // Story descriptor file: story.json
            Path story = zipFs.getPath("story.json");
            if (Files.notExists(story)) {
                return null;
            }
            StoryPackMetadata spMeta = objectMapper.readValue(Files.readAllBytes(story), StoryPackMetadata.class);
            spMeta.setPackFormat(PackFormat.ARCHIVE);
            // set storypack uuid (if missing) from 1st node
            if (spMeta.getUuid() == null) {
                spMeta.setUuid(spMeta.getUuidFirst());
            }
            // Pack thumbnail
            Path thumb = zipFs.getPath("thumbnail.png");
            if (Files.exists(thumb)) {
                spMeta.setThumbnail(Files.readAllBytes(thumb));
            }
            return spMeta;
        }
    }

    public StoryPack read(Path zipPath) throws IOException {
        // Zip archive contains a json file and separate assets
        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, ClassLoader.getSystemClassLoader())) {
            // Story descriptor file: story.json
            Path story = zipFs.getPath("story.json");
            if (Files.notExists(story)) {
                return null;
            }
            StoryPack sp = objectMapper.readValue(Files.readAllBytes(story), StoryPack.class);
            // Make sure the first node is actually 'square one'
            List<StageNode> stageNodes = sp.getStageNodes();
            int i = indexOfFirst(stageNodes);
            if (i > 0) { // move tagged node to 1st position
                stageNodes.add(0, stageNodes.remove(i));
            } else if (i < 0) { // tag 1st node
                stageNodes.get(0).setSquareOne(Boolean.TRUE);
            }
            // set storypack uuid (if missing) from 1st node
            if (sp.getUuid() == null) {
                sp.setUuid(stageNodes.get(0).getUuid());
            }
            // Read assets
            Path assetsDir = zipFs.getPath("assets/");
            stageNodes.parallelStream().map(StageNode::getImage).filter(Objects::nonNull)
                    .forEach(ThrowingConsumer.unchecked(a -> {
                        a.guessType();
                        a.setRawData(Files.readAllBytes(assetsDir.resolve(a.getName())));
                    }));
            stageNodes.parallelStream().map(StageNode::getAudio).filter(Objects::nonNull)
                    .forEach(ThrowingConsumer.unchecked(a -> {
                        a.guessType();
                        a.setRawData(Files.readAllBytes(assetsDir.resolve(a.getName())));
                    }));
            return sp;
        }
    }

    /** Find index of node tagged 'square one'. */
    private static int indexOfFirst(List<StageNode> stageNodes) {
        for (int i = 0; i < stageNodes.size(); i++) {
            if (Boolean.TRUE.equals(stageNodes.get(i).getSquareOne())) {
                return i;
            }
        }
        return -1;
    }
}
