/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.plainpk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import studio.core.v1.model.*;
import studio.core.v1.model.enriched.EnrichedPackMetadata;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

public class PlainPkStoryPackReader {

    private static final Logger LOGGER = Logger.getLogger(PlainPkStoryPackReader.class.getName());

    public static final String NODE_INDEX_FILENAME = "ni";
    public static final String LIST_INDEX_FILENAME = "li.plain";
    public static final String IMAGE_INDEX_FILENAME = "ri.plain";
    public static final String IMAGE_FOLDER = "rf" + File.separator;
    public static final String SOUND_INDEX_FILENAME = "si.plain";
    public static final String SOUND_FOLDER = "sf" + File.separator;
    public static final String NIGHT_MODE_FILENAME = "nm";
    public static final String UUID_FILENAME = "uuid.bin";
    public static final String METADATA_FILENAME = "_metadata.json";
    public static final String THUMBNAIL_FILENAME = "_thumbnail.png";

    public StoryPack read(InputStream inputStream) throws IOException {
        // Zip archive contains plaintext FS-format story files + uuid file + optional metadata files
        ZipArchiveInputStream zis = new ZipArchiveInputStream(inputStream);

        // Store assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();

        // Story pack model
        boolean factoryDisabled = false;
        short version = 0;
        
        // Keep ri/si index to stage nodes map
        Map<Integer, List<StageNode>> riIndexToStageNodes = new HashMap<>();
        Map<Integer, List<StageNode>> siIndexToStageNodes = new HashMap<>();

        TreeMap<Integer, StageNode> stageNodes = new TreeMap<>();                   // Keep stage nodes
        TreeMap<Integer, Integer> actionNodesOptionsCount = new TreeMap<>();        // Keep action nodes' options count
        TreeMap<Integer, List<Transition>> transitionsWithAction = new TreeMap<>(); // Transitions must be updated with the actual ActionNode

        // Enriched pack metadata
        EnrichedPackMetadata enrichedPack = null;
        boolean nightModeAvailable = false;

        String uuid = null;
        byte[] riContent = null;
        byte[] siContent = null;
        byte[] liContent = null;

        ZipArchiveEntry entry;
        while((entry = zis.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(UUID_FILENAME)) {
                DataInputStream dis = new DataInputStream(zis);
                long uuidHighBytes = dis.readLong();
                long uuidLowBytes = dis.readLong();
                uuid = (new UUID(uuidHighBytes, uuidLowBytes)).toString();
            }
            // Night mode is available if file 'nm' exists
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(NIGHT_MODE_FILENAME)) {
                nightModeAvailable = true;
            }
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(IMAGE_INDEX_FILENAME)) {
                riContent = IOUtils.toByteArray(zis);
            }
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(SOUND_INDEX_FILENAME)) {
                siContent = IOUtils.toByteArray(zis);
            }
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(LIST_INDEX_FILENAME)) {
                liContent = IOUtils.toByteArray(zis);
            }
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(NODE_INDEX_FILENAME)) {
                DataInputStream niDis = new DataInputStream(zis);
                ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
                // Nodes index file format version (1)
                bb.getShort();
                // Story pack version
                version = bb.getShort();
                // Start of actual nodes list in this file (0x200 / 512)
                int nodesList = bb.getInt();
                // Size of a stage node in this file (0x2C / 44)
                int nodeSize = bb.getInt();
                // Number of stage nodes in this file
                int stageNodesCount = bb.getInt();
                // Number of images (in RI file and rf/ folder)
                int imageAssetsCount = bb.getInt();
                // Number of sounds (in SI file and sf/ folder)
                int soundAssetsCount = bb.getInt();
                // Is factory pack (boolean) set to true to avoid pack inspection by official Luniistore application
                factoryDisabled = bb.get() != 0x00;

                // Read stage nodes
                for (int i=0; i<stageNodesCount; i++) {
                    bb = ByteBuffer.wrap(niDis.readNBytes(nodeSize)).order(ByteOrder.LITTLE_ENDIAN);
                    int imageAssetIndexInRI = bb.getInt();
                    int soundAssetIndexInSI = bb.getInt();
                    int okTransitionActionNodeIndexInLI = bb.getInt();
                    int okTransitionNumberOfOptions = bb.getInt();
                    int okTransitionSelectedOptionIndex = bb.getInt();
                    int homeTransitionActionNodeIndexInLI = bb.getInt();
                    int homeTransitionNumberOfOptions = bb.getInt();
                    int homeTransitionSelectedOptionIndex = bb.getInt();
                    boolean wheel = bb.getShort() != 0;
                    boolean ok = bb.getShort() != 0;
                    boolean home = bb.getShort() != 0;
                    boolean pause = bb.getShort() != 0;
                    boolean autoplay = bb.getShort() != 0;

                    // Transition will be updated later with the actual action nodes
                    Transition okTransition = null;
                    if (okTransitionActionNodeIndexInLI != -1 && okTransitionNumberOfOptions != -1 && okTransitionSelectedOptionIndex != -1) {
                        if (!actionNodesOptionsCount.containsKey(okTransitionActionNodeIndexInLI)) {
                            actionNodesOptionsCount.put(okTransitionActionNodeIndexInLI, okTransitionNumberOfOptions);
                        }
                        okTransition = new Transition(null, (short) okTransitionSelectedOptionIndex);
                        List<Transition> twa = transitionsWithAction.getOrDefault(okTransitionActionNodeIndexInLI, new ArrayList<>());
                        twa.add(okTransition);
                        transitionsWithAction.put(okTransitionActionNodeIndexInLI, twa);
                    }
                    Transition homeTransition = null;
                    if (homeTransitionActionNodeIndexInLI != -1 && homeTransitionNumberOfOptions != -1 && homeTransitionSelectedOptionIndex != -1) {
                        if (!actionNodesOptionsCount.containsKey(homeTransitionActionNodeIndexInLI)) {
                            actionNodesOptionsCount.put(homeTransitionActionNodeIndexInLI, homeTransitionNumberOfOptions);
                        }
                        homeTransition = new Transition(null, (short) homeTransitionSelectedOptionIndex);
                        List<Transition> twa = transitionsWithAction.getOrDefault(homeTransitionActionNodeIndexInLI, new ArrayList<>());
                        twa.add(homeTransition);
                        transitionsWithAction.put(homeTransitionActionNodeIndexInLI, twa);
                    }

                    StageNode stageNode = new StageNode(
                            i == 0 ? null : UUID.randomUUID().toString(),   // First node should have the same UUID as the story pack
                            null,
                            null,
                            okTransition,
                            homeTransition,
                            new ControlSettings(
                                    wheel,
                                    ok,
                                    home,
                                    pause,
                                    autoplay
                            ),
                            null
                    );

                    // Read Image and audio assets
                    if (imageAssetIndexInRI != -1) {
                        // Keep reference to image index
                        List<StageNode> atsn = riIndexToStageNodes.getOrDefault(imageAssetIndexInRI, new ArrayList<>());
                        atsn.add(stageNode);
                        riIndexToStageNodes.put(imageAssetIndexInRI, atsn);
                    }
                    if (soundAssetIndexInSI != -1) {
                        // Keep reference to audio index
                        List<StageNode> atsn = siIndexToStageNodes.getOrDefault(soundAssetIndexInSI, new ArrayList<>());
                        atsn.add(stageNode);
                        siIndexToStageNodes.put(soundAssetIndexInSI, atsn);
                    }

                    stageNodes.put(i, stageNode);
                }
            }
            // Optional metadata
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(METADATA_FILENAME)) {
                JsonParser parser = new JsonParser();
                JsonObject root = parser.parse(new InputStreamReader(zis)).getAsJsonObject();
                // Read (optional) enriched pack metadata
                Optional<String> maybeTitle = Optional.ofNullable(root.get("title")).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
                Optional<String> maybeDescription = Optional.ofNullable(root.get("description")).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
                if (maybeTitle.isPresent() || maybeDescription.isPresent()) {
                    enrichedPack = new EnrichedPackMetadata(maybeTitle.orElse(null), maybeDescription.orElse(null));
                }
            }
            // Optional thumbnail
            else if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(THUMBNAIL_FILENAME)) {
                // TODO Thumbnail in enrichedPack?
            }
            // Separate asset files
            else if (!entry.isDirectory() && (entry.getName().startsWith(IMAGE_FOLDER) || entry.getName().startsWith(SOUND_FOLDER))) {
                assets.put(entry.getName(), IOUtils.toByteArray(zis));
            }
        }

        zis.close();

        // First node should have the same UUID as the story pack
        StageNode squareOne = stageNodes.get(0);
        squareOne.setUuid(uuid);

        // Update assets in stage nodes
        for (Map.Entry<Integer, List<StageNode>> riEntry : riIndexToStageNodes.entrySet()) {
            // Read image path
            byte[] imagePath = Arrays.copyOfRange(riContent, riEntry.getKey()*12, riEntry.getKey()*12+12);   // Each entry takes 12 bytes
            String path = new String(imagePath, StandardCharsets.UTF_8);
            String imageAssetName = path.substring(4) + ".bmp";
            String assetKey = IMAGE_FOLDER + "000" + File.separator + imageAssetName;
            // Find asset
            if (assets.containsKey(assetKey)) {
                List<StageNode> stageNodesReferencingAsset = riEntry.getValue();
                if (stageNodesReferencingAsset != null && !stageNodesReferencingAsset.isEmpty()) {
                    for (StageNode stageNode : stageNodesReferencingAsset) {
                        stageNode.setImage(new ImageAsset("image/bmp", assets.get(assetKey), imageAssetName));
                    }
                }
            } else {
                LOGGER.warning("An image asset referenced by stage nodes is missing: " + imageAssetName);
            }
        }
        for (Map.Entry<Integer, List<StageNode>> siEntry : siIndexToStageNodes.entrySet()) {
            // Read audio path
            byte[] audioPath = Arrays.copyOfRange(siContent, siEntry.getKey()*12, siEntry.getKey()*12+12);   // Each entry takes 12 bytes
            String path = new String(audioPath, StandardCharsets.UTF_8);
            String audioAssetName = path.substring(4) + ".mp3";
            String assetKey = SOUND_FOLDER + "000" + File.separator + audioAssetName;
            // Find asset
            if (assets.containsKey(assetKey)) {
                List<StageNode> stageNodesReferencingAsset = siEntry.getValue();
                if (stageNodesReferencingAsset != null && !stageNodesReferencingAsset.isEmpty()) {
                    for (StageNode stageNode : stageNodesReferencingAsset) {
                        stageNode.setAudio(new AudioAsset("audio/mpeg", assets.get(assetKey), audioAssetName));
                    }
                }
            } else {
                LOGGER.warning("An audio asset referenced by stage nodes is missing: " + audioAssetName);
            }
        }

        // Read action nodes from 'li' file
        ByteBuffer liBb = ByteBuffer.wrap(liContent).order(ByteOrder.LITTLE_ENDIAN);
        for (Map.Entry<Integer, Integer> actionCount: actionNodesOptionsCount.entrySet()) {
            Integer offset = actionCount.getKey();
            Integer count = actionCount.getValue();
            List<StageNode> options = new ArrayList<>(count);
            liBb.position(offset*4);    // Each entry takes 4 bytes
            for (int i=0; i<count; i++) {
                int stageNodeIndex = liBb.getInt();
                options.add(stageNodes.get(stageNodeIndex));
            }
            // Update action on transitions referencing this sector
            ActionNode actionNode = new ActionNode(options, null);
            transitionsWithAction.get(offset).forEach(transition -> transition.setActionNode(actionNode));
        }

        return new StoryPack(uuid, factoryDisabled, version, List.copyOf(stageNodes.values()), enrichedPack, nightModeAvailable);
    }

}
