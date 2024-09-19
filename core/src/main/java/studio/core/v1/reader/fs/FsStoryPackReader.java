/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.fs;

import studio.core.v1.Constants;
import studio.core.v1.model.*;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.utils.BytesUtils;
import studio.core.v1.utils.XXTEACipher;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class FsStoryPackReader {

    private static final Logger LOGGER = Logger.getLogger(FsStoryPackReader.class.getName());

    public static final String NODE_INDEX_FILENAME = "ni";
    public static final String LIST_INDEX_FILENAME = "li";
    public static final String IMAGE_INDEX_FILENAME = "ri";
    public static final String IMAGE_FOLDER = "rf" + File.separator;
    public static final String SOUND_INDEX_FILENAME = "si";
    public static final String SOUND_FOLDER = "sf" + File.separator;
    public static final String NIGHT_MODE_FILENAME = "nm";
    public static final String CLEARTEXT_FILENAME = ".cleartext";
    public static final byte[] CLEARTEXT_RI_BEGINNING = "000\\".getBytes(StandardCharsets.UTF_8);

    public StoryPackMetadata readMetadata(Path inputFolder) throws IOException {
        // Pack metadata model
        StoryPackMetadata metadata = new StoryPackMetadata(Constants.PACK_FORMAT_FS);

        // Open 'ni' file
        File packFolder = inputFolder.toFile();
        FileInputStream niFis = new FileInputStream(new File(packFolder, NODE_INDEX_FILENAME));
        DataInputStream niDis = new DataInputStream(niFis);
        ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
        metadata.setVersion(bb.getShort(2));
        niDis.close();
        niFis.close();

        // Folder name is the uuid (minus the eventual timestamp, so we just trim everything starting at the dot)
        String uuid = inputFolder.getFileName().toString().split("\\.", 2)[0];
        metadata.setUuid(uuid);

        // Night mode is available if file 'nm' exists
        metadata.setNightModeAvailable(new File(packFolder, NIGHT_MODE_FILENAME).exists());

        return metadata;
    }

    public StoryPack read(Path inputFolder) throws IOException {
        TreeMap<Integer, StageNode> stageNodes = new TreeMap<>();                   // Keep stage nodes
        TreeMap<Integer, Integer> actionNodesOptionsCount = new TreeMap<>();        // Keep action nodes' options count
        TreeMap<Integer, List<Transition>> transitionsWithAction = new TreeMap<>(); // Transitions must be updated with the actual ActionNode

        // Folder name is the uuid (minus the eventual timestamp, so we just trim everything starting at the dot)
        String uuid = inputFolder.getFileName().toString().split("\\.", 2)[0];

        File packFolder = inputFolder.toFile();

        // Night mode is available if file 'nm' exists
        boolean nightModeAvailable = new File(packFolder, NIGHT_MODE_FILENAME).exists();

        // Pack files should be kept as cleartext in the library, with cipher operations happening during transfer to/from device
        // We keep a backward-compatibility with packs in library ciphered for v2

        // Assets are cleartext if file '.cleartext' exists
        boolean isCleartext = isCleartext(inputFolder, true);

        // Load ri, si and li files
        byte[] riContent = readCipheredFile(new File(packFolder, IMAGE_INDEX_FILENAME).toPath(), isCleartext);
        byte[] siContent = readCipheredFile(new File(packFolder, SOUND_INDEX_FILENAME).toPath(), isCleartext);
        byte[] liContent = readCipheredFile(new File(packFolder, LIST_INDEX_FILENAME).toPath(), isCleartext);

        // Open 'ni' file
        FileInputStream niFis = new FileInputStream(new File(packFolder, NODE_INDEX_FILENAME));
        DataInputStream niDis = new DataInputStream(niFis);
        ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
        // Nodes index file format version (1)
        bb.getShort();
        // Story pack version
        short version = bb.getShort();
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
        boolean factoryDisabled = bb.get() != 0x00;

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
                byte[] imagePath = Arrays.copyOfRange(riContent, imageAssetIndexInRI*12, imageAssetIndexInRI*12+12);   // Each entry takes 12 bytes
                String path = new String(imagePath, StandardCharsets.UTF_8);
                // Read image file
                byte[] rfContent = readCipheredFile(new File(packFolder, IMAGE_FOLDER+path.replaceAll("\\\\", "/")).toPath(), isCleartext);
                image = new ImageAsset("image/bmp", rfContent, path);
            }
            AudioAsset audio = null;
            if (soundAssetIndexInSI != -1) {
                // Read audio path
                byte[] audioPath = Arrays.copyOfRange(siContent, soundAssetIndexInSI*12, soundAssetIndexInSI*12+12);    // Each entry takes 12 bytes
                String path = new String(audioPath, StandardCharsets.UTF_8);
                // Read audio file
                byte[] sfContent = readCipheredFile(new File(packFolder, SOUND_FOLDER+path.replaceAll("\\\\", "/")).toPath(), isCleartext);
                audio = new AudioAsset("audio/mpeg", sfContent, path);
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

        niDis.close();
        niFis.close();

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
    
    public boolean isCleartext(Path inputFolder, boolean fixBrokenCleartext) throws IOException {
        File packFolder = inputFolder.toFile();

        // Assets are cleartext if file '.cleartext' exists
        boolean isCleartext = new File(packFolder, CLEARTEXT_FILENAME).exists();

        if (fixBrokenCleartext) {
            // Fix broken story packs with missing .cleartext file
            byte[] riRawContent = Files.readAllBytes(new File(packFolder, IMAGE_INDEX_FILENAME).toPath());
            if (!isCleartext && Arrays.equals(riRawContent, 0, CLEARTEXT_RI_BEGINNING.length, CLEARTEXT_RI_BEGINNING, 0, CLEARTEXT_RI_BEGINNING.length)) {
                LOGGER.warning("Story pack contains cleartext data but is missing .cleartext file: fixing...");

                // Indicate that files are cleartext
                new File(packFolder, CLEARTEXT_FILENAME).createNewFile();

                isCleartext = true;
            }
        }
        
        return isCleartext;
    }

    private byte[] readCipheredFile(Path path, boolean isCleartext) throws IOException {
        byte[] content = Files.readAllBytes(path);
        return isCleartext ? content : decipherFirstBlockCommonKey(content);
    }

    private byte[] decipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        int[] dataInt = BytesUtils.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] decryptedInt = XXTEACipher.btea(dataInt, -(Math.min(128, data.length/4)), BytesUtils.toIntArray(XXTEACipher.COMMON_KEY, ByteOrder.BIG_ENDIAN));
        byte[] decryptedBlock = BytesUtils.toByteArray(decryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }

}
