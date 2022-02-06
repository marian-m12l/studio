/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.writer.fs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import studio.core.v1.Constants;
import studio.core.v1.model.ActionNode;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.asset.AudioAsset;
import studio.core.v1.model.asset.AudioType;
import studio.core.v1.model.asset.ImageAsset;
import studio.core.v1.model.asset.ImageType;
import studio.core.v1.utils.AudioConversion;
import studio.core.v1.utils.ID3Tags;
import studio.core.v1.utils.SecurityUtils;
import studio.core.v1.utils.XXTEACipher;
import studio.core.v1.writer.StoryPackWriter;

/**
 * Writer for the new binary format coming with firmware 2.4<br/>
 * Assets must be prepared to match the expected format : 4-bits depth / RLE
 * encoding BMP for images, and mono 44100Hz MP3 for sounds.<br/>
 * The first 512 bytes of most files are scrambled with a common key, provided
 * in an external file. <br/>
 * The bt file uses a device-specific key.
 */
public class FsStoryPackWriter implements StoryPackWriter {

    private static final Logger LOGGER = LogManager.getLogger(FsStoryPackWriter.class);

    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String LIST_INDEX_FILENAME = "li";
    private static final String IMAGE_INDEX_FILENAME = "ri";
    private static final String IMAGE_FOLDER = "rf" + File.separator;
    private static final String SOUND_INDEX_FILENAME = "si";
    private static final String SOUND_FOLDER = "sf" + File.separator;
    private static final String BOOT_FILENAME = "bt";
    private static final String NIGHT_MODE_FILENAME = "nm";

    /** Blank MP3 file. */
    public static final byte[] BLANK_MP3 = readRelative("blank.mp3");

    // TODO Enriched metadata in a dedicated file (pack's title, description and thumbnail, nodes' name, group, type and position)

    public void write(StoryPack pack, Path packFolder, boolean enriched) throws IOException {
        // Write night mode
        if (pack.isNightModeAvailable()) {
            Files.createFile(packFolder.resolve(NIGHT_MODE_FILENAME));
        }

        // Store assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();
        // Keep track of action nodes and assets
        List<ActionNode> actionNodesOrdered = new ArrayList<>();
        Map<ActionNode, Integer> actionNodesIndexes = new HashMap<>();
        List<String> imageHashOrdered = new ArrayList<>();
        List<String> audioHashOrdered = new ArrayList<>();

        // Add nodes index file: ni
        Path niPath = packFolder.resolve(NODE_INDEX_FILENAME);
        try( DataOutputStream niDos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(niPath)))) {
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
                    .map(SecurityUtils::sha1Hex)
                    .distinct()
                    .count());
            // Number of sounds (in SI file and sf/ folder)
            bb.putInt((int) pack.getStageNodes().stream()
                    .map(StageNode::getAudio)
                    .filter(Objects::nonNull)
                    .map(AudioAsset::getRawData)
                    .map(SecurityUtils::sha1Hex)
                    .distinct()
                    .count());
            // Is factory pack (boolean) set to true to avoid pack inspection by official Luniistore application
            bb.put((byte) 1);

            // Jump to address 0x200 for actual list of nodes
            bb.put(new byte[512-25]);
            niDos.write(bb.array());
            bb.clear();

            // Write stage nodes
            int nextActionNodeIndex = 0;
            for (int i = 0; i < pack.getStageNodes().size(); i++) {
                StageNode node = pack.getStageNodes().get(i);

                int imageIndex = -1;
                ImageAsset image = node.getImage();
                if (image != null) {
                    byte[] imageData = image.getRawData();
                    String imageHash = SecurityUtils.sha1Hex(imageData);
                    if (!imageHashOrdered.contains(imageHash)) {
                        if (ImageType.BMP != image.getType()) {
                            throw new IllegalArgumentException("FS pack file requires image assets to be BMP.");
                        }
                        ByteBuffer bmpBuffer = ByteBuffer.wrap(imageData);
                        bmpBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        // Make sure the BMP file is RLE-compressed / 4-bits depth
                        if (bmpBuffer.getShort(28) != 0x0004 || bmpBuffer.getInt(30) != 0x00000002) {
                            throw new IllegalArgumentException("FS pack file requires image assets to use 4-bit depth and RLE encoding.");
                        }
                        // Check image dimensions
                        if (bmpBuffer.getInt(18) != 320 || bmpBuffer.getInt(22) != 240) {
                            throw new IllegalArgumentException("FS pack file requires image assets to be 320x240 pixels.");
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
                // If audio is missing, add a blank audio to satisfy the device
                if (audio == null) {
                    audio = new AudioAsset(AudioType.MP3, BLANK_MP3);
                }
                byte[] audioData = audio.getRawData();
                String audioHash = SecurityUtils.sha1Hex(audioData);
                if (!audioHashOrdered.contains(audioHash)) {
                    if (AudioType.MP3 != audio.getType() && AudioType.MPEG != audio.getType()) {
                        throw new IllegalArgumentException("FS pack file requires audio assets to be MP3.");
                    } else {
                        // Check ID3 tags
                        if (ID3Tags.hasID3v1Tag(audioData) || ID3Tags.hasID3v2Tag(audioData)) {
                            throw new IllegalArgumentException("FS pack file does not support ID3 tags in MP3 files.");
                        }
                        // Check that the file is MONO / 44100Hz
                        try {
                            AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(audioData));
                            if (audioFileFormat.getFormat().getChannels() != AudioConversion.CHANNELS
                                    || audioFileFormat.getFormat().getSampleRate() != AudioConversion.MP3_SAMPLE_RATE) {
                                throw new IllegalArgumentException("FS pack file requires MP3 audio assets to be MONO / 44100Hz.");
                            }
                        } catch (UnsupportedAudioFileException e) {
                            throw new IllegalArgumentException("Unsupported Audio File",e);
                        }
                    }
                    audioIndex = audioHashOrdered.size();
                    audioHashOrdered.add(audioHash);
                    assets.putIfAbsent(audioHash, audioData);
                } else {
                    audioIndex = audioHashOrdered.indexOf(audioHash);
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
        }

        // Add lists index file: li
        Path liPath = packFolder.resolve(LIST_INDEX_FILENAME);
        try (ByteArrayOutputStream liBaos = new ByteArrayOutputStream();
                DataOutputStream liDos = new DataOutputStream(liBaos)) {
            // Add action nodes
            for (ActionNode actionNode : actionNodesOrdered) {
                // Each option points to a stage node by index in Nodes Index file (ni)
                writeActionNode(liDos, actionNode.getOptions().stream() //
                        .mapToInt(stage -> pack.getStageNodes().indexOf(stage)).toArray());
            }
            // write File
            writeCypheredFile(liPath, liBaos.toByteArray());
        }

        // Add images index file: ri
        Path riPath = packFolder.resolve(IMAGE_INDEX_FILENAME);
        try (ByteArrayOutputStream riBaos = new ByteArrayOutputStream();
                DataOutputStream riDos = new DataOutputStream(riBaos)) {
            // For each image asset: 12-bytes relative path (e.g. 000\11111111)
            for (int i = 0; i < imageHashOrdered.size(); i++) {
                // Write image path into ri file
                String imageHash = imageHashOrdered.get(i);
                String rfSubPath = assetPathFromIndex(i);
                riDos.write(rfSubPath.getBytes(StandardCharsets.UTF_8));
                // Write image data into file
                Path rfPath = packFolder.resolve(IMAGE_FOLDER + rfSubPath.replace('\\', '/'));
                Files.createDirectories(rfPath.getParent());
                writeCypheredFile(rfPath, assets.get(imageHash));
            }
            writeCypheredFile(riPath, riBaos.toByteArray());
        }

        // Add sound index file: si
        Path siPath = packFolder.resolve(SOUND_INDEX_FILENAME);
        try (ByteArrayOutputStream siBaos = new ByteArrayOutputStream();
                DataOutputStream siDos = new DataOutputStream(siBaos)) {
            // For each image asset: 12-bytes relative path (e.g. 000\11111111)
            for (int i = 0; i < audioHashOrdered.size(); i++) {
                // Write sound path into si file
                String audioHash = audioHashOrdered.get(i);
                String sfSubPath = assetPathFromIndex(i);
                siDos.write(sfSubPath.getBytes(StandardCharsets.UTF_8));
                // Write sound data into file
                Path sfPath = packFolder.resolve(SOUND_FOLDER + sfSubPath.replace('\\', '/'));
                Files.createDirectories(sfPath.getParent());
                writeCypheredFile(sfPath, assets.get(audioHash));
            }
            writeCypheredFile(siPath, siBaos.toByteArray());
        }
    }

    private void writeCypheredFile(Path path, byte[] byteArray) throws IOException {
        // The first block of bytes must be ciphered with the common key
        byte[] liCiphered = cipherFirstBlockCommonKey(byteArray);
        Files.write(path, liCiphered);
    }

    public void addBootFile(Path packFolder, byte[] deviceUuid) throws IOException {
        // Compute specific key
        byte[] specificKey = computeSpecificKeyFromUUID(deviceUuid);
        Path riPath = packFolder.resolve(IMAGE_INDEX_FILENAME);
        Path btPath = packFolder.resolve(BOOT_FILENAME);
        try (InputStream is = new BufferedInputStream(Files.newInputStream(riPath))) {
            int cypherBlockSize = 64;
            // Read ciphered block of ri file
            byte[] riCipheredBlock = is.readNBytes(cypherBlockSize);
            // The first **scrambled** 64 bytes of 'ri' file must be ciphered with the
            // device-specific key into 'bt' file
            byte[] btCiphered = cipher(CipherMode.CIPHER, riCipheredBlock, cypherBlockSize, specificKey);
            // Add boot file: bt
            Files.write(btPath, btCiphered);
        }
    }
    
    // Create pack folder: last 8 digits of uuid
    public static Path createPackFolder(StoryPack storyPack, Path tmp) throws IOException {
        Path packFolder = tmp.resolve(transformUuid(UUID.fromString(storyPack.getUuid())));
        return Files.createDirectories(packFolder);
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

    enum CipherMode {
        CIPHER, DECIPHER
    }

    /** (De-)cipher a block of data. */
    private byte[] cipher(CipherMode mode, byte[] data, int minSize, byte[] key) {
        byte[] block = Arrays.copyOfRange(data, 0, Math.min(minSize, data.length));
        int[] dataInt = XXTEACipher.toIntArray(block, ByteOrder.LITTLE_ENDIAN);
        int[] keyInt = XXTEACipher.toIntArray(key, ByteOrder.BIG_ENDIAN);
        int op = Math.min(128, data.length / 4);
        int[] encryptedInt = XXTEACipher.btea(dataInt, mode == CipherMode.DECIPHER ? -op : op, keyInt);
        return XXTEACipher.toByteArray(encryptedInt, ByteOrder.LITTLE_ENDIAN);
    }

    private byte[] cipherFirstBlockCommonKey(byte[] data) {
        byte[] encryptedBlock = cipher(CipherMode.CIPHER, data, 512, Constants.COMMON_KEY);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(encryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }

    private byte[] decipherFirstBlockCommonKey(byte[] data) {
        byte[] decryptedBlock = cipher(CipherMode.DECIPHER, data, 512, Constants.COMMON_KEY);
        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(decryptedBlock);
        if (data.length > 512) {
            bb.put(Arrays.copyOfRange(data, 512, data.length));
        }
        return bb.array();
    }

    private byte[] computeSpecificKeyFromUUID(byte[] uuid) {
        byte[] btKey = decipherFirstBlockCommonKey(uuid);
        return new byte[] { //
                btKey[11], btKey[10], btKey[9], btKey[8], //
                btKey[15], btKey[14], btKey[13], btKey[12], //
                btKey[3], btKey[2], btKey[1], btKey[0], //
                btKey[7], btKey[6], btKey[5], btKey[4] //
        };
    }

    /** Read classpath relative file. */
    private static byte[] readRelative(String relative) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Path p = Path.of(classLoader.getResource(relative).toURI());
            return Files.readAllBytes(p);
        } catch (URISyntaxException | IOException e) {
            LOGGER.atError().withThrowable(e).log("Cannot load relative resource {}!", relative);
            return new byte[0];
        }
    }
}
