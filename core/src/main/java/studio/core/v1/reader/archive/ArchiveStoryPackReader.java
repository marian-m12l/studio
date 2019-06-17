/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.archive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import studio.core.v1.model.*;
import studio.core.v1.model.metadata.StoryPackMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ArchiveStoryPackReader {

    public StoryPackMetadata readMetadata(InputStream inputStream) throws IOException {
        // Zip archive contains a json file and separate assets
        ZipInputStream zis = new ZipInputStream(inputStream);

        // Pack metadata model
        StoryPackMetadata metadata = new StoryPackMetadata();


        ZipEntry entry;
        while((entry = zis.getNextEntry()) != null) {
            // Story descriptor file: story.json
            if (!entry.isDirectory() && entry.getName().equalsIgnoreCase("story.json")) {
                JsonParser parser = new JsonParser();
                JsonObject root = parser.parse(new InputStreamReader(zis)).getAsJsonObject();

                // Read metadata
                metadata.setVersion(root.get("version").getAsShort());
                Optional.ofNullable(root.get("title")).ifPresent(title ->
                        metadata.setTitle(title.getAsString())
                );
                Optional.ofNullable(root.get("description")).ifPresent(desc ->
                        metadata.setDescription(desc.getAsString())
                );

                // Read first stage node
                JsonObject mainStageNode = root.getAsJsonArray("stageNodes").get(0).getAsJsonObject();
                metadata.setUuid(mainStageNode.get("uuid").getAsString());
            }
            // Pack thumbnail
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase("thumbnail.png")) {
                metadata.setThumbnail(IOUtils.toByteArray(zis));
            }
            // Ignore asset files
            else if (!entry.isDirectory() && entry.getName().startsWith("assets/")) {
                // no-op
            }
        }

        zis.close();

        return metadata;
    }

    public StoryPack read(InputStream inputStream) throws IOException {

        // Zip archive contains a json file and separate assets
        ZipInputStream zis = new ZipInputStream(inputStream);

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


        ZipEntry entry;
        while((entry = zis.getNextEntry()) != null) {
            // Story descriptor file: story.json
            if (!entry.isDirectory() && entry.getName().equalsIgnoreCase("story.json")) {
                JsonParser parser = new JsonParser();
                JsonObject root = parser.parse(new InputStreamReader(zis)).getAsJsonObject();

                // Read metadata
                version = root.get("version").getAsShort();

                // Read action nodes
                TreeMap<String, ActionNode> actionNodes = new TreeMap<>();
                Iterator<JsonElement> actionsIter = root.getAsJsonArray("actionNodes").iterator();
                while (actionsIter.hasNext()) {
                    JsonObject node = actionsIter.next().getAsJsonObject();
                    actionNodes.put(node.get("id").getAsString(), new ActionNode());
                }

                // Read stage nodes
                Iterator<JsonElement> stagesIter = root.getAsJsonArray("stageNodes").iterator();
                while (stagesIter.hasNext()) {
                    JsonObject node = stagesIter.next().getAsJsonObject();
                    String uuid = node.get("uuid").getAsString();
                    Transition okTransition = null;
                    if (node.get("okTransition") != null && node.get("okTransition").isJsonObject()) {
                        ActionNode actionNode = actionNodes.get(node.getAsJsonObject("okTransition").get("actionNode").getAsString());
                        okTransition = new Transition(actionNode, node.getAsJsonObject("okTransition").get("optionIndex").getAsShort());
                    }
                    Transition homeTransition = null;
                    if (node.get("homeTransition") != null && node.get("homeTransition").isJsonObject()) {
                        ActionNode actionNode = actionNodes.get(node.getAsJsonObject("homeTransition").get("actionNode").getAsString());
                        homeTransition = new Transition(actionNode, node.getAsJsonObject("homeTransition").get("optionIndex").getAsShort());
                    }
                    JsonObject controlSettings = node.getAsJsonObject("controlSettings");

                    StageNode stageNode = new StageNode(
                            uuid,
                            null,
                            null,
                            okTransition,
                            homeTransition,
                            new ControlSettings(
                                    controlSettings.get("wheel").getAsBoolean(),
                                    controlSettings.get("ok").getAsBoolean(),
                                    controlSettings.get("home").getAsBoolean(),
                                    controlSettings.get("pause").getAsBoolean(),
                                    controlSettings.get("autoplay").getAsBoolean()
                            )
                    );

                    if (node.get("squareOne") != null && node.get("squareOne").getAsBoolean()) {
                        squareOne = stageNode;
                    }

                    if (node.get("image") != null) {
                        String imageAssetName = node.get("image").getAsString();
                        List<StageNode> atsn = assetToStageNodes.getOrDefault(imageAssetName, new ArrayList<>());
                        atsn.add(stageNode);
                        assetToStageNodes.put(imageAssetName, atsn);
                    }
                    if (node.get("audio") != null) {
                        String audioAssetName = node.get("audio").getAsString();
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
            String extension = assetName.substring(dotIndex+1);

            // Stage nodes explicitly reference their assets' filenames
            List<StageNode> stageNodesReferencingAsset = assetToStageNodes.get(assetName);
            if (stageNodesReferencingAsset != null && !stageNodesReferencingAsset.isEmpty()) {
                for (StageNode stageNode : stageNodesReferencingAsset) {
                    if (extension.equalsIgnoreCase("bmp")) {
                        stageNode.setImage(new ImageAsset(assetEntry.getValue()));
                    } else if (extension.equalsIgnoreCase("wav")) {
                        stageNode.setAudio(new AudioAsset(assetEntry.getValue()));
                    } else {
                        // Unsupported asset
                    }
                }
            }
        }

        zis.close();

        // Make sure the first node is actually 'square one'
        List<StageNode> nodes = new ArrayList<>(stageNodes.values());
        if (squareOne != null) {
            nodes.remove(squareOne);
            nodes.add(0, squareOne);
        }

        return new StoryPack(factoryDisabled, version, nodes);
    }
}
