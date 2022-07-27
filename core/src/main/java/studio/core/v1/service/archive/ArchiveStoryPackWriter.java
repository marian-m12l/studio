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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.stream.JsonWriter;

import studio.core.v1.model.ActionNode;
import studio.core.v1.model.Node;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.enriched.EnrichedNodePosition;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.enriched.EnrichedPackMetadata;
import studio.core.v1.service.StoryPackWriter;
import studio.core.v1.utils.security.SecurityUtils;

public class ArchiveStoryPackWriter implements StoryPackWriter {

    private static final Map<String, String> ZIPFS_OPTIONS = Map.of("create", "true");

    /** Jackson writer. */
    private ObjectWriter objectWriter = new ObjectMapper().setSerializationInclusion(Include.NON_NULL)
            .writerWithDefaultPrettyPrinter();

    // -- Jackson

    public void write(StoryPack pack, Path zipPath, boolean enriched) throws IOException {
        // Store assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();
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
            Optional.ofNullable(sn.getAudio()).ifPresent(a -> {
                assets.putIfAbsent(a.getName(), a.getRawData());
            });
            Optional.ofNullable(sn.getImage()).ifPresent(a -> {
                assets.putIfAbsent(a.getName(), a.getRawData());
            });
        }

        // Zip archive contains a json file and separate assets
        URI uri = URI.create("jar:" + zipPath.toUri());
        try (FileSystem zipFs = FileSystems.newFileSystem(uri, ZIPFS_OPTIONS);
                Writer jsonWriter = Files.newBufferedWriter(zipFs.getPath("story.json"))) {
            // Add story descriptor file: story.json
            objectWriter.writeValue(jsonWriter, pack);
            // Add assets in separate directory
            Path assetPath = Files.createDirectories(zipFs.getPath("assets/"));
            for (Map.Entry<String, byte[]> a : assets.entrySet()) {
                Files.write(assetPath.resolve(a.getKey()), a.getValue());
            }
        }
    }

    // -- GSON

    @Deprecated
    public void write1(StoryPack pack, Path zipPath, boolean enriched) throws IOException {
        // Zip archive contains a json file and separate assets
        URI uri = URI.create("jar:" + zipPath.toUri());
        try (FileSystem zipFs = FileSystems.newFileSystem(uri, ZIPFS_OPTIONS)) {
            // Store assets bytes
            TreeMap<String, byte[]> assets = new TreeMap<>();
            // Add story descriptor file: story.json
            writeStoryJson(pack, zipFs.getPath("story.json"), assets);
            // Add assets in separate directory
            Path assetPath = Files.createDirectories(zipFs.getPath("assets/"));
            for (Map.Entry<String, byte[]> a : assets.entrySet()) {
                Files.write(assetPath.resolve(a.getKey()), a.getValue());
            }
        }
    }

    private static void writeStoryJson(StoryPack pack, Path storyPath, TreeMap<String, byte[]> assets)
            throws IOException {
        try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(storyPath))) {
            // Start json document
            writer.setIndent("    ");
            writer.beginObject();

            // Write file format metadata
            writer.name("format").value("v1");

            // Write (optional) enriched pack metadata
            if (pack.getEnriched() != null) {
                String packTitle = pack.getEnriched().getTitle();
                writer.name("title").value(packTitle != null ? packTitle : "MISSING_PACK_TITLE");
                if (pack.getEnriched().getDescription() != null) {
                    writer.name("description").value(pack.getEnriched().getDescription());
                }
                // TODO Thumbnail?
            }

            // Write metadata
            writer.name("version").value(pack.getVersion());

            // Write night mode
            writer.name("nightModeAvailable").value(pack.isNightModeAvailable());

            // Write stage nodes and keep track of action nodes and assets
            Map<ActionNode, String> actionNodeToId = new HashMap<>();
            writer.name("stageNodes");
            writer.beginArray();
            for (int i = 0; i < pack.getStageNodes().size(); i++) {
                StageNode node = pack.getStageNodes().get(i);
                writer.beginObject();
                writer.name("uuid").value(node.getUuid());
                // Write (optional) enriched node metadata
                if (node.getEnriched() != null) {
                    writeEnrichedNodeMetadata(writer, node);
                }
                // The first stage node is marked as such
                if (i == 0) {
                    writer.name("squareOne").value(true);
                }
                writer.name("image");
                if (node.getImage() == null) {
                    writer.nullValue();
                } else {
                    byte[] imageData = node.getImage().getRawData();
                    String extension = node.getImage().getType().getFirstExtension();
                    String assetFileName = SecurityUtils.sha1Hex(imageData) + extension;
                    writer.value(assetFileName);
                    assets.putIfAbsent(assetFileName, imageData);
                }
                writer.name("audio");
                if (node.getAudio() == null) {
                    writer.nullValue();
                } else {
                    byte[] audioData = node.getAudio().getRawData();
                    String extension = node.getAudio().getType().getFirstExtension();
                    String assetFileName = SecurityUtils.sha1Hex(audioData) + extension;
                    writer.value(assetFileName);
                    assets.putIfAbsent(assetFileName, audioData);
                }
                writer.name("okTransition");
                if (node.getOkTransition() == null) {
                    writer.nullValue();
                } else {
                    writer.beginObject();
                    if (!actionNodeToId.containsKey(node.getOkTransition().getActionNode())) {
                        actionNodeToId.put(node.getOkTransition().getActionNode(), UUID.randomUUID().toString());
                    }
                    writer.name("actionNode").value(actionNodeToId.get(node.getOkTransition().getActionNode()));
                    writer.name("optionIndex").value(node.getOkTransition().getOptionIndex());
                    writer.endObject();
                }
                writer.name("homeTransition");
                if (node.getHomeTransition() == null) {
                    writer.nullValue();
                } else {
                    writer.beginObject();
                    if (!actionNodeToId.containsKey(node.getHomeTransition().getActionNode())) {
                        actionNodeToId.put(node.getHomeTransition().getActionNode(), UUID.randomUUID().toString());
                    }
                    writer.name("actionNode").value(actionNodeToId.get(node.getHomeTransition().getActionNode()));
                    writer.name("optionIndex").value(node.getHomeTransition().getOptionIndex());
                    writer.endObject();
                }
                writer.name("controlSettings");
                writer.beginObject();
                writer.name("wheel").value(node.getControlSettings().isWheelEnabled());
                writer.name("ok").value(node.getControlSettings().isOkEnabled());
                writer.name("home").value(node.getControlSettings().isHomeEnabled());
                writer.name("pause").value(node.getControlSettings().isPauseEnabled());
                writer.name("autoplay").value(node.getControlSettings().isAutoJumpEnabled());
                writer.endObject();
                writer.endObject();
            }
            writer.endArray();

            writer.name("actionNodes");
            writer.beginArray();
            for (Map.Entry<ActionNode, String> actionNode : actionNodeToId.entrySet()) {
                writer.beginObject();
                writer.name("id").value(actionNode.getValue());

                // Write (optional) enriched node metadata
                ActionNode node = actionNode.getKey();
                if (node.getEnriched() != null) {
                    writeEnrichedNodeMetadata(writer, node);
                }

                writer.name("options");
                writer.beginArray();
                for (StageNode option : node.getOptions()) {
                    writer.value(option.getUuid());
                }
                writer.endArray();
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
            // cleanup
            actionNodeToId.clear();
        }
    }

    private static void writeEnrichedNodeMetadata(JsonWriter writer, Node node) throws IOException {
        String nodeName = node.getEnriched().getName();
        writer.name("name").value(nodeName != null ? nodeName : "MISSING_NAME");
        EnrichedNodeType nodeType = node.getEnriched().getType();
        if (nodeType != null) {
            writer.name("type").value(nodeType.label);
        }
        String nodeGroupId = node.getEnriched().getGroupId();
        if (nodeGroupId != null) {
            writer.name("groupId").value(nodeGroupId);
        }
        EnrichedNodePosition nodePosition = node.getEnriched().getPosition();
        if (nodePosition != null) {
            writer.name("position");
            writer.beginObject();
            writer.name("x").value(nodePosition.getX());
            writer.name("y").value(nodePosition.getY());
            writer.endObject();
        }
    }

}
