/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import studio.core.v1.Constants;
import studio.core.v1.model.ActionNode;
import studio.core.v1.model.AudioAsset;
import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.ImageAsset;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;
import studio.core.v1.model.enriched.EnrichedNodePosition;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.enriched.EnrichedPackMetadata;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.model.mime.AudioType;
import studio.core.v1.model.mime.ImageType;

public class ArchiveStoryPackReader {

    public StoryPackMetadata readMetadata(InputStream inputStream) throws IOException {
        // Zip archive contains a json file and separate assets
        try (ZipInputStream zis = new ZipInputStream(inputStream); InputStreamReader isr = new InputStreamReader(zis)) {

            // Pack metadata model
            StoryPackMetadata metadata = new StoryPackMetadata(Constants.PACK_FORMAT_ARCHIVE);

            boolean hasStoryJsonEntry = false;

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                // Story descriptor file: story.json
                if (entry.getName().equalsIgnoreCase("story.json")) {
                    hasStoryJsonEntry = true;

                    JsonParser parser = new JsonParser();
                    JsonObject root = parser.parse(isr).getAsJsonObject();

                    // Read metadata
                    metadata.setVersion(root.get("version").getAsShort());
                    Optional.ofNullable(root.get("title")).filter(JsonElement::isJsonPrimitive)
                            .ifPresent(title -> metadata.setTitle(title.getAsString()));
                    Optional.ofNullable(root.get("description")).filter(JsonElement::isJsonPrimitive)
                            .ifPresent(desc -> metadata.setDescription(desc.getAsString()));
                    // TODO Thumbnail?

                    // Night mode
                    metadata.setNightModeAvailable(Optional.ofNullable(root.get("nightModeAvailable"))
                            .map(JsonElement::getAsBoolean).orElse(false));

                    // Read first stage node
                    JsonObject mainStageNode = root.getAsJsonArray("stageNodes").get(0).getAsJsonObject();
                    metadata.setUuid(mainStageNode.get("uuid").getAsString());
                }
                // Pack thumbnail
                else if (entry.getName().equalsIgnoreCase("thumbnail.png")) {
                    metadata.setThumbnail(IOUtils.toByteArray(zis));
                }
                // Ignore asset files
                else if (entry.getName().startsWith("assets/")) {
                    // no-op
                }
            }
            return hasStoryJsonEntry ? metadata : null;
        }
    }

    public StoryPack read(InputStream inputStream) throws IOException {

        // Zip archive contains a json file and separate assets
        try (ZipInputStream zis = new ZipInputStream(inputStream); InputStreamReader isr = new InputStreamReader(zis)) {

            // Store assets bytes
            TreeMap<String, byte[]> assets = new TreeMap<>();

            // Story pack model
            boolean factoryDisabled = false;
            short version = 0;
            // Keep stage nodes in the order they appear
            LinkedHashMap<String, StageNode> stageNodes = new LinkedHashMap<>();
            // Keep asset name to stage nodes map
            Map<String, List<StageNode>> assetToStageNodes = new HashMap<>();
            // Keep first node
            StageNode squareOne = null;
            // Enriched pack metadata
            EnrichedPackMetadata enrichedPack = null;
            boolean nightModeAvailable = false;

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Story descriptor file: story.json
                if (!entry.isDirectory() && entry.getName().equalsIgnoreCase("story.json")) {
                    JsonParser parser = new JsonParser();
                    JsonObject root = parser.parse(isr).getAsJsonObject();

                    // Read metadata
                    version = root.get("version").getAsShort();
                    // Read (optional) enriched pack metadata
                    Optional<String> maybeTitle = Optional.ofNullable(root.get("title"))
                            .filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
                    Optional<String> maybeDescription = Optional.ofNullable(root.get("description"))
                            .filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
                    // TODO Thumbnail?
                    if (maybeTitle.isPresent() || maybeDescription.isPresent()) {
                        enrichedPack = new EnrichedPackMetadata(maybeTitle.orElse(null), maybeDescription.orElse(null));
                    }

                    // Night mode
                    nightModeAvailable = Optional.ofNullable(root.get("nightModeAvailable"))
                            .map(JsonElement::getAsBoolean).orElse(false);

                    // Read action nodes
                    TreeMap<String, ActionNode> actionNodes = new TreeMap<>();
                    Iterator<JsonElement> actionsIter = root.getAsJsonArray("actionNodes").iterator();
                    while (actionsIter.hasNext()) {
                        JsonObject node = actionsIter.next().getAsJsonObject();

                        // Read (optional) enriched node metadata
                        EnrichedNodeMetadata enrichedNodeMetadata = readEnrichedNodeMetadata(node);

                        actionNodes.put(node.get("id").getAsString(), new ActionNode(enrichedNodeMetadata));
                    }

                    // Read stage nodes
                    Iterator<JsonElement> stagesIter = root.getAsJsonArray("stageNodes").iterator();
                    while (stagesIter.hasNext()) {
                        JsonObject node = stagesIter.next().getAsJsonObject();
                        String uuid = node.get("uuid").getAsString();
                        Transition okTransition = null;
                        Transition homeTransition = null;

                        JsonElement okNode = node.get("okTransition");
                        if (okNode != null && okNode.isJsonObject()) {
                            JsonObject okObj = okNode.getAsJsonObject();
                            ActionNode actionNode = actionNodes.get(okObj.get("actionNode").getAsString());
                            okTransition = new Transition(actionNode, okObj.get("optionIndex").getAsShort());
                        }
                        JsonElement homeNode = node.get("homeTransition");
                        if (homeNode != null && homeNode.isJsonObject()) {
                            JsonObject homeObj = homeNode.getAsJsonObject();
                            ActionNode actionNode = actionNodes.get(homeObj.get("actionNode").getAsString());
                            homeTransition = new Transition(actionNode, homeObj.get("optionIndex").getAsShort());
                        }

                        JsonObject controlSettings = node.getAsJsonObject("controlSettings");

                        // Read (optional) enriched node metadata
                        EnrichedNodeMetadata enrichedNodeMetadata = readEnrichedNodeMetadata(node);

                        StageNode stageNode = new StageNode(uuid, null, null, okTransition, homeTransition,
                                new ControlSettings(controlSettings.get("wheel").getAsBoolean(),
                                        controlSettings.get("ok").getAsBoolean(),
                                        controlSettings.get("home").getAsBoolean(),
                                        controlSettings.get("pause").getAsBoolean(),
                                        controlSettings.get("autoplay").getAsBoolean()),
                                enrichedNodeMetadata);

                        if (node.get("squareOne") != null && node.get("squareOne").getAsBoolean()) {
                            squareOne = stageNode;
                        }
                        JsonElement imageNode = node.get("image");
                        if (imageNode != null && !imageNode.isJsonNull()) {
                            String imageAssetName = imageNode.getAsString();
                            List<StageNode> atsn = assetToStageNodes.getOrDefault(imageAssetName, new ArrayList<>());
                            atsn.add(stageNode);
                            assetToStageNodes.put(imageAssetName, atsn);
                        }
                        JsonElement audioNode = node.get("audio");
                        if (audioNode != null && !audioNode.isJsonNull()) {
                            String audioAssetName = audioNode.getAsString();
                            List<StageNode> atsn = assetToStageNodes.getOrDefault(audioAssetName, new ArrayList<>());
                            atsn.add(stageNode);
                            assetToStageNodes.put(audioAssetName, atsn);
                        }

                        stageNodes.put(uuid, stageNode);
                    }

                    // Link action nodes to stage nodes
                    actionsIter = root.getAsJsonArray("actionNodes").iterator();
                    while (actionsIter.hasNext()) {
                        JsonObject node = actionsIter.next().getAsJsonObject();
                        ActionNode actionNode = actionNodes.get(node.get("id").getAsString());
                        List<StageNode> options = new ArrayList<>();
                        Iterator<JsonElement> optionsIter = node.getAsJsonArray("options").iterator();
                        while (optionsIter.hasNext()) {
                            String stageUuid = optionsIter.next().getAsString();
                            options.add(stageNodes.get(stageUuid));
                        }
                        actionNode.setOptions(options);
                    }

                }
                // Separate asset files
                else if (!entry.isDirectory() && entry.getName().startsWith("assets/")) {
                    assets.put(entry.getName().substring("assets/".length()), IOUtils.toByteArray(zis));
                }
            }

            // Update assets in stage nodes
            for (Map.Entry<String, byte[]> assetEntry : assets.entrySet()) {
                String assetName = assetEntry.getKey();
                int dotIndex = assetName.lastIndexOf(".");
                String extension = assetName.substring(dotIndex).toLowerCase();

                // Stage nodes explicitly reference their assets' filenames
                List<StageNode> stageNodesReferencingAsset = assetToStageNodes.get(assetName);
                if (stageNodesReferencingAsset != null && !stageNodesReferencingAsset.isEmpty()) {
                    for (StageNode stageNode : stageNodesReferencingAsset) {
                        // supported images
                        ImageType it = ImageType.fromExtension(extension);
                        if(it != null) {
                            stageNode.setImage(new ImageAsset(it.getMime(), assetEntry.getValue()));
                            continue;
                        }
                        // supported audio
                        AudioType at = AudioType.fromExtension(extension);
                        if(at != null) {
                            stageNode.setAudio(new AudioAsset(at.getMime(), assetEntry.getValue()));
                            continue;
                        }
                        // Unsupported asset
                    }
                }
            }

            // Make sure the first node is actually 'square one'
            List<StageNode> nodes = new ArrayList<>(stageNodes.values());
            if (squareOne != null) {
                nodes.remove(squareOne);
                nodes.add(0, squareOne);
            }
            return new StoryPack(nodes.get(0).getUuid(), factoryDisabled, version, nodes, enrichedPack,
                    nightModeAvailable);
        }
    }

    private EnrichedNodeMetadata readEnrichedNodeMetadata(JsonObject node) {
        Optional<String> maybeName = Optional.ofNullable(node.get("name")).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
        Optional<String> maybeType = Optional.ofNullable(node.get("type")).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
        Optional<String> maybeGroupId = Optional.ofNullable(node.get("groupId")).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
        Optional<JsonObject> maybePosition = Optional.ofNullable(node.get("position")).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
        if (maybeName.isPresent() || maybeType.isPresent() || maybeGroupId.isPresent() || maybePosition.isPresent()) {
            return new EnrichedNodeMetadata(
                    maybeName.orElse(null),
                    maybeType.map(EnrichedNodeType::fromLabel).orElse(null),
                    maybeGroupId.orElse(null),
                    maybePosition
                            .map(position -> new EnrichedNodePosition(
                                    position.get("x").getAsShort(),
                                    position.get("y").getAsShort()))
                            .orElse(null)
            );
        }
        return null;
    }
}
