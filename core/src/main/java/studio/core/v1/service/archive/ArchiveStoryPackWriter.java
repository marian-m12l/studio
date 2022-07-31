/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.service.archive;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.service.StoryPackWriter;

public class ArchiveStoryPackWriter implements StoryPackWriter {

    private static final Map<String, String> ZIPFS_OPTIONS = Map.of("create", "true");

    /** Jackson writer. */
    private ObjectWriter objectWriter = new ObjectMapper().setSerializationInclusion(Include.NON_NULL)
            .writerWithDefaultPrettyPrinter();

    public void write(StoryPack pack, Path zipPath, boolean enriched) throws IOException {
        // Store assets bytes
        Map<String, byte[]> assetMap = new TreeMap<>();
        // Fix missing title
        if (pack.getEnriched() != null && pack.getEnriched().getTitle() == null) {
            pack.getEnriched().setTitle("MISSING_PACK_TITLE");
        }
        // Tag first node
        StageNode snFirst = pack.getStageNodes().get(0);
        if (!Boolean.TRUE.equals(snFirst.getSquareOne())) {
            snFirst.setSquareOne(Boolean.TRUE);
        }
        // Add media
        for (StageNode sn : pack.getStageNodes()) {
            // Fix missing node name
            if (sn.getEnriched() != null && sn.getEnriched().getName() == null) {
                sn.getEnriched().setName("MISSING_NAME");
            }
            // cache assets
            sn.assets().forEach(a -> assetMap.putIfAbsent(a.getName(), a.getRawData()));
        }
        // Zip archive contains a json file and separate assets
        URI uri = URI.create("jar:" + zipPath.toUri());
        try (FileSystem zipFs = FileSystems.newFileSystem(uri, ZIPFS_OPTIONS);
                Writer jsonWriter = Files.newBufferedWriter(zipFs.getPath("story.json"))) {
            // Add story descriptor file: story.json
            objectWriter.writeValue(jsonWriter, pack);
            // Add assets in separate directory
            Path assetPath = Files.createDirectories(zipFs.getPath("assets/"));
            for (Map.Entry<String, byte[]> a : assetMap.entrySet()) {
                Files.write(assetPath.resolve(a.getKey()), a.getValue());
            }
        }
    }
}
