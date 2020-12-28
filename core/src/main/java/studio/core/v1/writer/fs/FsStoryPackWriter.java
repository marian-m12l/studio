/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.writer.fs;

import org.apache.commons.codec.digest.DigestUtils;
import studio.core.v1.model.*;
import studio.core.v1.utils.XXTEACipher;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

/*
Writer for the new binary format coming with firmware 2.4
Assets must be prepared to match the expected format : 4-bits depth / RLE encoding BMP for images, and mono 44100Hz MP3 for sounds.
The first 512 bytes of most files are scrambled with a common key, provided in an external file. The bt file uses a
device-specific key.
 */
public class FsStoryPackWriter {

    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String LIST_INDEX_FILENAME = "li";
    private static final String IMAGE_INDEX_FILENAME = "ri";
    private static final String IMAGE_FOLDER = "rf" + File.separator;
    private static final String SOUND_INDEX_FILENAME = "si";
    private static final String SOUND_FOLDER = "sf" + File.separator;
    private static final String BOOT_FILENAME = "bt";

    private final byte[] deviceUuid;
    private final byte[] commonKey;

    public FsStoryPackWriter(byte[] deviceUuid, byte[] commonKey) {
        this.deviceUuid = deviceUuid;
        this.commonKey = commonKey;
    }

    // TODO Enriched metadata in a dedicated file (pack's title, description and thumbnail, nodes' name, group, type and position)

    public Path write(StoryPack pack, Path outputFolder) throws IOException {
        // Compute specific key
        byte[] specificKey = computeSpecificKeyFromUUID(deviceUuid);

        // Create pack folder: last 8 digits of uuid
        File packFolder = new File(outputFolder.toFile(), transformUuid(UUID.fromString(pack.getUuid())));
        packFolder.mkdirs();

        // Store assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();

        // Add nodes index file: ni
        FileOutputStream niFos = new FileOutputStream(new File(packFolder, NODE_INDEX_FILENAME));
        DataOutputStream niDos = new DataOutputStream(niFos);
        ByteBuffer bb = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);

        // Nodes index file format version (1)
        bb.putShort((short) 1);
        // Story pack version (1)
        bb.putShort(pack.getVersion());
        // Start of actual nodes list in this file (0x200 / 512)
        bb.putInt(512);
        // Size of a stage node in this file (0x2C / 44)
        bb.putInt(44);
        // Number of stage nodes in this file
        bb.putInt(pack.getStageNodes().size());
        // Number of images (in RI file and rf/ folder)
        bb.putInt((int) pack.getStageNodes().stream()
                .map(StageNode::getImage)
                .filter(Objects::nonNull)
                .map(ImageAsset::getRawData)
                .map(DigestUtils::sha1Hex)
                .distinct()
                .count());
        // Number of sounds (in SI file and sf/ folder)
        bb.putInt((int) pack.getStageNodes().stream()
                .map(StageNode::getAudio)
                .filter(Objects::nonNull)
                .map(AudioAsset::getRawData)
                .map(DigestUtils::sha1Hex)
                .distinct()
                .count());
        // Is factory pack (boolean) set to true to avoid pack inspection by official Luniistore application
        bb.put((byte) 1);

        // Jump to address 0x200 for actual list of nodes
        bb.put(new byte[512-25]);
        niDos.write(bb.array());
        bb.clear();

        // Write stage nodes and keep track of action nodes and assets
        List<ActionNode> actionNodesOrdered = new ArrayList<>();
        Map<ActionNode, Integer> actionNodesIndexes = new HashMap<>();
        int nextActionNodeIndex = 0;
        List<String> imageHashOrdered = new ArrayList<>();
        List<String> audioHashOrdered = new ArrayList<>();
        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);

            int imageIndex = -1;
            ImageAsset image = node.getImage();
            if (image != null) {
                byte[] imageData = image.getRawData();
                String imageHash = DigestUtils.sha1Hex(imageData);
                if (!imageHashOrdered.contains(imageHash)) {
                    // Make sure the BMP file is RLE-compressed / 4-bits depth
                    if (!"image/bmp".equals(image.getMimeType()) || imageData[28] != 0x04 || imageData[30] != 0x02) {
                        throw new IllegalArgumentException("FS pack file requires image assets to use 4-bit depth and RLE encoding.");
                    }
                    imageIndex = imageHashOrdered.size();
                    imageHashOrdered.add(imageHash);
                    assets.putIfAbsent(imageHash, imageData);
                } else {
                    imageIndex = imageHashOrdered.indexOf(imageHash);
                }
            }
            int audioIndex = -1;
            AudioAsset audio = node.getAudio();
            if (audio != null) {
                byte[] audioData = audio.getRawData();
                String audioHash = DigestUtils.sha1Hex(audioData);
                if (!audioHashOrdered.contains(audioHash)) {
                    // TODO Check that the file is in MONO / 44100Hz
                    if (!"audio/mp3".equals(audio.getMimeType()) && !"audio/mpeg".equals(audio.getMimeType())) {
                        throw new IllegalArgumentException("FS pack file requires audio assets to be MP3.");
                    }
                    audioIndex = audioHashOrdered.size();
                    audioHashOrdered.add(audioHash);
                    assets.putIfAbsent(audioHash, audioData);
                } else {
                    audioIndex = audioHashOrdered.indexOf(audioHash);
                }
            }
            Transition okTransition = node.getOkTransition();
            if (okTransition != null && !actionNodesOrdered.contains(okTransition.getActionNode())) {
                actionNodesOrdered.add(okTransition.getActionNode());
                actionNodesIndexes.put(okTransition.getActionNode(), nextActionNodeIndex);
                nextActionNodeIndex += okTransition.getActionNode().getOptions().size();
            }
            Transition homeTransition = node.getHomeTransition();
            if (homeTransition != null && !actionNodesOrdered.contains(homeTransition.getActionNode())) {
                actionNodesOrdered.add(homeTransition.getActionNode());
                actionNodesIndexes.put(homeTransition.getActionNode(), nextActionNodeIndex);
                nextActionNodeIndex += homeTransition.getActionNode().getOptions().size();
            }
            writeStageNode(
                    niDos,
                    imageIndex,  // Image index in RI file (index 0 == first image) --> rf/000/11111111
                    audioIndex,  // Sound index in SI file (index 0 == first sound) --> sf/000/11111111
                    okTransition == null ? -1 : actionNodesIndexes.get(okTransition.getActionNode()),  // OK transition: Action node index in LI file (index 0 == first action node)
                    okTransition == null ? -1 : okTransition.getActionNode().getOptions().size(),  // OK transition: Number of options available
                    okTransition == null ? -1 : okTransition.getOptionIndex(),  // OK transition: Menu option index (index 0 == first menu option)
                    homeTransition == null ? -1 : actionNodesIndexes.get(homeTransition.getActionNode()), // HOME transition: Action node index in LI file (-1 == no transition)
                    homeTransition == null ? -1 : homeTransition.getActionNode().getOptions().size(), // HOME transition: Number of options available
                    homeTransition == null ? -1 : homeTransition.getOptionIndex(), // HOME transition: Menu option index
                    node.getControlSettings().isWheelEnabled(),   // WHEEL flag
                    node.getControlSettings().isOkEnabled(),   // OK flag
                    node.getControlSettings().isHomeEnabled(),  // HOME flag
                    node.getControlSettings().isPauseEnabled(),  // PAUSE flag
                    node.getControlSettings().isAutoJumpEnabled()   // AUTOPLAY flag
            );
        }
        niDos.close();
        niFos.close();


        // Add lists index file: li
        FileOutputStream liFos = new FileOutputStream(new File(packFolder, LIST_INDEX_FILENAME));
        ByteArrayOutputStream liBaos = new ByteArrayOutputStream();
        DataOutputStream liDos = new DataOutputStream(liBaos);
        // Add action nodes
        for (ActionNode actionNode : actionNodesOrdered) {
            writeActionNode(
                    liDos,
                    actionNode.getOptions().stream().mapToInt(stage -> pack.getStageNodes().indexOf(stage)).toArray()   // Each option points to a stage node by index in Nodes Index file (ni)
            );
        }
        liDos.close();
        liBaos.close();
        byte[] liBytes = liBaos.toByteArray();
        // The first block of bytes must be ciphered with the common key
        byte[] liCiphered = cipherFirstBlockCommonKey(liBytes);
        liFos.write(liCiphered);
        liFos.close();


        // Add images index file: ri
        FileOutputStream riFos = new FileOutputStream(new File(packFolder, IMAGE_INDEX_FILENAME));
        ByteArrayOutputStream riBaos = new ByteArrayOutputStream();
        DataOutputStream riDos = new DataOutputStream(riBaos);
        // For each image asset: 12-bytes relative path (e.g. 000\11111111)
        for (int i=0; i<imageHashOrdered.size(); i++) {
            // Write image path into ri file
            String imageHash = imageHashOrdered.get(i);
            String rfPath = assetPathFromIndex(i);
            riDos.write(rfPath.getBytes(Charset.forName("UTF-8")));
            // Write image data into file
            File rfFile = new File(packFolder, IMAGE_FOLDER + rfPath.replace('\\', '/'));
            rfFile.getParentFile().mkdirs();
            FileOutputStream rfFos = new FileOutputStream(rfFile);
            byte[] rfBytes = assets.get(imageHash);
            // The first block of bytes must be ciphered with the common key
            byte[] rfCiphered = cipherFirstBlockCommonKey(rfBytes);
            rfFos.write(rfCiphered);
            rfFos.close();
        }
        riDos.close();
        riBaos.close();
        byte[] riBytes = riBaos.toByteArray();
        // The first block of bytes must be ciphered with the common key
        byte[] riCiphered = cipherFirstBlockCommonKey(riBytes);
        riFos.write(riCiphered);
        riFos.close();


        // Add sound index file: si
        FileOutputStream siFos = new FileOutputStream(new File(packFolder, SOUND_INDEX_FILENAME));
        ByteArrayOutputStream siBaos = new ByteArrayOutputStream();
        DataOutputStream siDos = new DataOutputStream(siBaos);
        // For each image asset: 12-bytes relative path (e.g. 000\11111111)
        for (int i=0; i<audioHashOrdered.size(); i++) {
            // Write sound path into si file
            String audioHash = audioHashOrdered.get(i);
            String sfPath = assetPathFromIndex(i);
            siDos.write(sfPath.getBytes(Charset.forName("UTF-8")));
            // Write sound data into file
            File sfFile = new File(packFolder, SOUND_FOLDER + sfPath.replace('\\', '/'));
            sfFile.getParentFile().mkdirs();
            FileOutputStream sfFos = new FileOutputStream(sfFile);
            byte[] sfBytes = assets.get(audioHash);
            // The first block of bytes must be ciphered with the common key
            byte[] sfCiphered = cipherFirstBlockCommonKey(sfBytes);
            sfFos.write(sfCiphered);
            sfFos.close();
        }
        siDos.close();
        siBaos.close();
        byte[] siBytes = siBaos.toByteArray();
        // The first block of bytes must be ciphered with the common key
        byte[] siCiphered = cipherFirstBlockCommonKey(siBytes);
        siFos.write(siCiphered);
        siFos.close();


        // Add boot file: bt
        FileOutputStream btFos = new FileOutputStream(new File(packFolder, BOOT_FILENAME));
        // The first **scrambled** 64 bytes of 'ri' file must be ciphered with the device-specific key into 'bt' file
        byte[] btCiphered = cipherFirstBlockSpecificKey(Arrays.copyOfRange(riCiphered, 0, Math.min(64, riCiphered.length)), specificKey);
        btFos.write(btCiphered);
        btFos.close();

        return packFolder.toPath();
    }

    private static String transformUuid(UUID uuid) {
        String uuidStr = uuid.toString().replaceAll("-", "");
        return uuidStr.substring(uuidStr.length()-8).toUpperCase();
    }

    private static void writeStageNode(
            DataOutputStream niDos,
            int imageAssetIndexInRI,
            int soundAssetIndexInSI,
            int okTransitionActionNodeIndexInLI,
            int okTransitionNumberOfOptions,
            int okTransitionSelectedOptionIndex,
            int homeTransitionActionNodeIndexInLI,
            int homeTransitionNumberOfOptions,
            int homeTransitionSelectedOptionIndex,
            boolean wheel,
            boolean ok,
            boolean home,
            boolean pause,
            boolean autoplay
    ) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(imageAssetIndexInRI);
        bb.putInt(soundAssetIndexInSI);
        bb.putInt(okTransitionActionNodeIndexInLI);
        bb.putInt(okTransitionNumberOfOptions);
        bb.putInt(okTransitionSelectedOptionIndex);
        bb.putInt(homeTransitionActionNodeIndexInLI);
        bb.putInt(homeTransitionNumberOfOptions);
        bb.putInt(homeTransitionSelectedOptionIndex);
        bb.putShort(boolToShort(wheel));
        bb.putShort(boolToShort(ok));
        bb.putShort(boolToShort(home));
        bb.putShort(boolToShort(pause));
        bb.putShort(boolToShort(autoplay));
        bb.putShort((short) 0);

        niDos.write(bb.array());
        bb.clear();
    }

    private static short boolToShort(boolean b) {
        return (short) (b ? 1 : 0);
    }

    private static void writeActionNode(
            DataOutputStream liDos,
            int[] stageNodesIndexes
    ) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(stageNodesIndexes.length*4).order(ByteOrder.LITTLE_ENDIAN);
        for (int stageNodeIndex : stageNodesIndexes) {
            bb.putInt(stageNodeIndex);
        }
        liDos.write(bb.array());
        bb.clear();
    }

    private static String assetPathFromIndex(int index) {
        return String.format("000\\%08d", index);
    }

    private byte[] cipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] encryptedInt = XXTEACipher.btea(dataInt, Math.min(128, data.length/4), XXTEACipher.toIntArray(commonKey, ByteOrder.BIG_ENDIAN));
        byte[] encryptedBlock = XXTEACipher.toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(encryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }
    private byte[] decipherFirstBlockCommonKey(byte[] data) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(512, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] decryptedInt = XXTEACipher.btea(dataInt, -(Math.min(128, data.length/4)), XXTEACipher.toIntArray(commonKey, ByteOrder.BIG_ENDIAN));
        byte[] decryptedBlock = XXTEACipher.toByteArray(decryptedInt, ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }
    private byte[] computeSpecificKeyFromUUID(byte[] uuid) {
        byte[] btKey = decipherFirstBlockCommonKey(uuid);
        byte[] reorderedBtKey = new byte[]{
                btKey[11], btKey[10], btKey[9], btKey[8],
                btKey[15], btKey[14], btKey[13], btKey[12],
                btKey[3], btKey[2], btKey[1], btKey[0],
                btKey[7], btKey[6], btKey[5], btKey[4]
        };
        return reorderedBtKey;
    }
    private byte[] cipherFirstBlockSpecificKey(byte[] data, byte[] specificKey) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(64, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] encryptedInt = XXTEACipher.btea(dataInt, Math.min(128, data.length/4), XXTEACipher.toIntArray(specificKey, ByteOrder.BIG_ENDIAN));
        return XXTEACipher.toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
    }

}
