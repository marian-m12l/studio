/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.fs;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import studio.core.v1.Constants;
import studio.core.v1.model.ActionNode;
import studio.core.v1.model.AudioAsset;
import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.ImageAsset;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.model.mime.AudioType;
import studio.core.v1.model.mime.ImageType;
import studio.core.v1.reader.StoryPackReader;
import studio.core.v1.utils.XXTEACipher;

public class FsStoryPackReader implements StoryPackReader {

    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String LIST_INDEX_FILENAME = "li";
    private static final String IMAGE_INDEX_FILENAME = "ri";
    private static final String IMAGE_FOLDER = "rf" + File.separator;
    private static final String SOUND_INDEX_FILENAME = "si";
    private static final String SOUND_FOLDER = "sf" + File.separator;
    private static final String NIGHT_MODE_FILENAME = "nm";

    public StoryPackMetadata readMetadata(Path inputFolder) throws IOException {
        // Pack metadata model
        StoryPackMetadata metadata = new StoryPackMetadata(Constants.PACK_FORMAT_FS);

        // Open 'ni' file
        Path niPath = inputFolder.resolve(NODE_INDEX_FILENAME);
        try(DataInputStream niDis = new DataInputStream(Files.newInputStream(niPath, StandardOpenOption.READ))) {
            ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
            metadata.setVersion(bb.getShort(2));
        }

        // Folder name is the uuid (minus the eventual timestamp, so we just trim everything starting at the dot)
        String uuid = inputFolder.getFileName().toString().split("\\.", 2)[0];
        metadata.setUuid(uuid);

        // Night mode is available if file 'nm' exists
        Path nmPath = inputFolder.resolve(NIGHT_MODE_FILENAME);
        metadata.setNightModeAvailable(Files.exists(nmPath));

        return metadata;
    }

    public StoryPack read(Path inputFolder) throws IOException {
        TreeMap<Integer, StageNode> stageNodes = new TreeMap<>();                   // Keep stage nodes
        TreeMap<Integer, Integer> actionNodesOptionsCount = new TreeMap<>();        // Keep action nodes' options count
        TreeMap<Integer, List<Transition>> transitionsWithAction = new TreeMap<>(); // Transitions must be updated with the actual ActionNode

        // Folder name is the uuid (minus the eventual timestamp, so we just trim everything starting at the dot)
        String uuid = inputFolder.getFileName().toString().split("\\.", 2)[0];

        // Night mode is available if file 'nm' exists
        Path nmPath = inputFolder.resolve(NIGHT_MODE_FILENAME);
        boolean nightModeAvailable = Files.exists(nmPath);

        // Load ri, si and li files
        byte[] riContent = readCipheredFile(inputFolder.resolve(IMAGE_INDEX_FILENAME));
        byte[] siContent = readCipheredFile(inputFolder.resolve(SOUND_INDEX_FILENAME));
        byte[] liContent = readCipheredFile(inputFolder.resolve(LIST_INDEX_FILENAME));

        // Story pack version
        short version;
        // Is factory pack (boolean) set to true to avoid pack inspection by official Luniistore application
        boolean factoryDisabled;

        // Open 'ni' file
        Path niPath = inputFolder.resolve(NODE_INDEX_FILENAME);
        try(DataInputStream niDis = new DataInputStream(Files.newInputStream(niPath))) {
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
    
                // Read Image and audio assets
                ImageAsset image = null;
                if (imageAssetIndexInRI != -1) {
                    // Read image path
                    byte[] imageEntry = Arrays.copyOfRange(riContent, imageAssetIndexInRI*12, imageAssetIndexInRI*12+12);   // Each entry takes 12 bytes
                    String imageName = new String(imageEntry, StandardCharsets.UTF_8).replaceAll("\\\\", "/");
                    // Read image file
                    Path imagePath = inputFolder.resolve(IMAGE_FOLDER + imageName);
                    byte[] rfContent = readCipheredFile(imagePath);
                    image = new ImageAsset(ImageType.BMP.getMime(), rfContent);
                }
                AudioAsset audio = null;
                if (soundAssetIndexInSI != -1) {
                    // Read audio path
                    byte[] audioEntry = Arrays.copyOfRange(siContent, soundAssetIndexInSI*12, soundAssetIndexInSI*12+12);    // Each entry takes 12 bytes
                    String audioName = new String(audioEntry, StandardCharsets.UTF_8).replaceAll("\\\\", "/");
                    // Read audio file
                    Path audioPath = inputFolder.resolve(SOUND_FOLDER + audioName);
                    byte[] sfContent = readCipheredFile(audioPath);
                    audio = new AudioAsset(AudioType.MPEG.getMime(), sfContent);
                }

                StageNode stageNode = new StageNode(
                        i == 0 ? uuid : UUID.randomUUID().toString(), // First node should have the same UUID as the story pack FIXME node uuids from metadata file
                        image,
                        audio,
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
                stageNodes.put(i, stageNode);
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

        return new StoryPack(uuid, factoryDisabled, version, List.copyOf(stageNodes.values()), null, nightModeAvailable);
    }

    private byte[] readCipheredFile(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        return decipherFirstBlockCommonKey(content);
    }

    private byte[] decipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] decryptedInt = XXTEACipher.btea(dataInt, -(Math.min(128, data.length/4)), XXTEACipher.toIntArray(XXTEACipher.COMMON_KEY, ByteOrder.BIG_ENDIAN));
        byte[] decryptedBlock = XXTEACipher.toByteArray(decryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }

}
