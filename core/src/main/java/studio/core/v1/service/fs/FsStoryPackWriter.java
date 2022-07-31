/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.service.fs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import studio.core.v1.model.ActionNode;
import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.asset.MediaAsset;
import studio.core.v1.model.asset.MediaAssetType;
import studio.core.v1.service.StoryPackWriter;
import studio.core.v1.service.fs.FsStoryPackDTO.FsStoryPack;
import studio.core.v1.utils.audio.AudioConversion;
import studio.core.v1.utils.audio.ID3Tags;
import studio.core.v1.utils.security.XXTEACipher;
import studio.core.v1.utils.security.XXTEACipher.CipherMode;

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

    /** Blank MP3 file. */
    private static final byte[] BLANK_MP3 = readRelative("blank.mp3");

    // TODO Enriched metadata in a dedicated file (pack's title, description and
    // thumbnail, nodes' name, group, type and position)

    public void write(StoryPack pack, Path packFolder, boolean enriched) throws IOException {
        // Create output dir
        Files.createDirectories(packFolder);
        FsStoryPack fsp = new FsStoryPack(packFolder);
        // Write night mode
        if (pack.isNightModeAvailable()) {
            Files.createFile(fsp.getNightMode());
        }

        // Store assets bytes
        Map<String, byte[]> assets = new TreeMap<>();
        // Keep track of action nodes and assets
        List<ActionNode> actionNodesOrdered = new ArrayList<>();
        Map<ActionNode, Integer> actionNodesIndexes = new HashMap<>();
        List<String> imageHashOrdered = new ArrayList<>();
        List<String> audioHashOrdered = new ArrayList<>();

        // Add nodes index file: ni
        try (OutputStream niDos = new BufferedOutputStream(Files.newOutputStream(fsp.getNodeIndex()))) {
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
            bb.putInt((int) pack.getStageNodes().stream().map(StageNode::getImage).filter(Objects::nonNull)
                    .map(MediaAsset::getName).distinct().count());
            // Number of sounds (in SI file and sf/ folder)
            bb.putInt((int) pack.getStageNodes().stream().map(StageNode::getAudio).filter(Objects::nonNull)
                    .map(MediaAsset::getName).distinct().count());
            // Is factory pack (boolean) set to true to avoid pack inspection by official
            // Luniistore application
            bb.put((byte) 1);

            // Jump to address 0x200 for actual list of nodes
            bb.put(new byte[512 - 25]);
            niDos.write(bb.array());
            bb.clear();

            // Write stage nodes
            int nextActionNodeIndex = 0;
            for (int i = 0; i < pack.getStageNodes().size(); i++) {
                StageNode node = pack.getStageNodes().get(i);

                int imageIndex = -1;
                MediaAsset image = node.getImage();
                if (image != null) {
                    String imageHash = image.findHash();
                    if (!imageHashOrdered.contains(imageHash)) {
                        if (MediaAssetType.BMP != image.getType()) {
                            throw new IllegalArgumentException("FS pack file requires image assets to be BMP.");
                        }
                        byte[] imageData = image.getRawData();
                        ByteBuffer bmpBuffer = ByteBuffer.wrap(imageData).order(ByteOrder.LITTLE_ENDIAN);
                        // Make sure the BMP file is RLE-compressed / 4-bits depth
                        if (bmpBuffer.getShort(28) != 0x0004 || bmpBuffer.getInt(30) != 0x00000002) {
                            throw new IllegalArgumentException(
                                    "FS pack file requires image assets to use 4-bit depth and RLE encoding.");
                        }
                        // Check image dimensions
                        if (bmpBuffer.getInt(18) != 320 || bmpBuffer.getInt(22) != 240) {
                            throw new IllegalArgumentException(
                                    "FS pack file requires image assets to be 320x240 pixels.");
                        }
                        imageIndex = imageHashOrdered.size();
                        imageHashOrdered.add(imageHash);
                        assets.putIfAbsent(imageHash, imageData);
                    } else {
                        imageIndex = imageHashOrdered.indexOf(imageHash);
                    }
                }
                int audioIndex = -1;
                MediaAsset audio = node.getAudio();
                // If audio is missing, add a blank audio to satisfy the device
                if (audio == null) {
                    audio = new MediaAsset(MediaAssetType.MP3, BLANK_MP3);
                }
                String audioHash = audio.findHash();
                if (!audioHashOrdered.contains(audioHash)) {
                    if (MediaAssetType.MP3 != audio.getType()) {
                        throw new IllegalArgumentException("FS pack file requires audio assets to be MP3.");
                    } 
                    byte[] audioData = audio.getRawData();
                    // Check ID3 tags
                    if (ID3Tags.hasID3v1Tag(audioData) || ID3Tags.hasID3v2Tag(audioData)) {
                        throw new IllegalArgumentException("FS pack file does not support ID3 tags in MP3 files.");
                    }
                    // Check that the file is MONO / 44100Hz
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData)) {
                        AudioFormat audioFormat = AudioSystem.getAudioFileFormat(bais).getFormat();
                        if (audioFormat.getChannels() != AudioConversion.CHANNELS
                                || audioFormat.getSampleRate() != AudioConversion.MP3_SAMPLE_RATE) {
                            throw new IllegalArgumentException(
                                    "FS pack file requires MP3 audio assets to be MONO / 44100Hz.");
                        }
                    } catch (UnsupportedAudioFileException e) {
                        throw new IllegalArgumentException("Unsupported Audio File", e);
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

                // writeStageNode
                ControlSettings ctrl = node.getControlSettings();
                bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
                // Image index in RI file (index 0 == first image) --> rf/000/11111111
                bb.putInt(imageIndex);
                // Sound index in SI file (index 0 == first sound) --> sf/000/11111111
                bb.putInt(audioIndex);
                // OK transition
                if (okTransition != null) {
                    // Action node index in LI file (index 0 == first action node)
                    bb.putInt(actionNodesIndexes.get(okTransition.getActionNode()));
                    // Number of options available
                    bb.putInt(okTransition.getActionNode().getOptions().size());
                    // Menu option index (index 0 == first menu option)
                    bb.putInt(okTransition.getOptionIndex());
                } else {
                    bb.putInt(-1).putInt(-1).putInt(-1);
                }
                // HOME transition
                if (homeTransition != null) {
                    // Action node index in LI file (-1 == no transition)
                    bb.putInt(actionNodesIndexes.get(homeTransition.getActionNode()));
                    // Number of options available
                    bb.putInt(homeTransition.getActionNode().getOptions().size());
                    // Menu option index
                    bb.putInt(homeTransition.getOptionIndex());
                } else {
                    bb.putInt(-1).putInt(-1).putInt(-1);
                }
                // WHEEL flag
                bb.putShort(boolToShort(ctrl.isWheelEnabled()));
                // OK flag
                bb.putShort(boolToShort(ctrl.isOkEnabled()));
                // HOME flag
                bb.putShort(boolToShort(ctrl.isHomeEnabled()));
                // PAUSE flag
                bb.putShort(boolToShort(ctrl.isPauseEnabled()));
                // AUTOPLAY flag
                bb.putShort(boolToShort(ctrl.isAutoJumpEnabled()));
                bb.putShort((short) 0);

                niDos.write(bb.array());
                bb.clear();
            }
        }

        // cleanup
        actionNodesIndexes.clear();

        // Add lists index file: li
        try (ByteArrayOutputStream liBaos = new ByteArrayOutputStream();
                DataOutputStream liDos = new DataOutputStream(liBaos)) {
            // Add action nodes
            for (ActionNode actionNode : actionNodesOrdered) {
                // Each option points to a stage node by index in Nodes Index file (ni)
                writeActionNode(liDos, actionNode.getOptions().stream() //
                        .mapToInt(stage -> pack.getStageNodes().indexOf(stage)).toArray());
            }
            // write File
            writeCypheredFile(fsp.getListIndex(), liBaos.toByteArray());
        }

        // Add images index file: ri
        try (ByteArrayOutputStream riBaos = new ByteArrayOutputStream();
                DataOutputStream riDos = new DataOutputStream(riBaos)) {
            // For each image asset: 12-bytes relative path (e.g. 000\11111111)
            for (int i = 0; i < imageHashOrdered.size(); i++) {
                // Write image path into ri file
                String imageHash = imageHashOrdered.get(i);
                String rfSubPath = assetPathFromIndex(i);
                riDos.write(rfSubPath.getBytes(StandardCharsets.UTF_8));
                // Write image data into file
                Path rfPath = fsp.getImageFolder().resolve(rfSubPath.replace('\\', '/'));
                Files.createDirectories(rfPath.getParent());
                writeCypheredFile(rfPath, assets.get(imageHash));
            }
            writeCypheredFile(fsp.getImageIndex(), riBaos.toByteArray());
        }

        // Add sound index file: si
        try (ByteArrayOutputStream siBaos = new ByteArrayOutputStream();
                DataOutputStream siDos = new DataOutputStream(siBaos)) {
            // For each image asset: 12-bytes relative path (e.g. 000\11111111)
            for (int i = 0; i < audioHashOrdered.size(); i++) {
                // Write sound path into si file
                String audioHash = audioHashOrdered.get(i);
                String sfSubPath = assetPathFromIndex(i);
                siDos.write(sfSubPath.getBytes(StandardCharsets.UTF_8));
                // Write sound data into file
                Path sfPath = fsp.getSoundFolder().resolve(sfSubPath.replace('\\', '/'));
                Files.createDirectories(sfPath.getParent());
                writeCypheredFile(sfPath, assets.get(audioHash));
            }
            writeCypheredFile(fsp.getSoundIndex(), siBaos.toByteArray());
        }
    }

    private static void writeCypheredFile(Path path, byte[] byteArray) throws IOException {
        // The first block of bytes must be ciphered with the common key
        byte[] ciphered = XXTEACipher.cipherCommonKey(CipherMode.CIPHER, byteArray);
        Files.write(path, ciphered);
    }

    public static void addBootFile(Path packFolder, byte[] deviceUuid) throws IOException {
        FsStoryPack fsp = new FsStoryPack(packFolder);
        // Compute specific key
        byte[] specificKey = computeSpecificKeyFromUUID(deviceUuid);
        try (InputStream is = new BufferedInputStream(Files.newInputStream(fsp.getImageIndex()))) {
            int cypherBlockSize = 64;
            // Read ciphered block of ri file
            byte[] riCipheredBlock = is.readNBytes(cypherBlockSize);
            // The first **scrambled** 64 bytes of 'ri' file must be ciphered with the
            // device-specific key into 'bt' file
            byte[] btCiphered = XXTEACipher.cipher(CipherMode.CIPHER, riCipheredBlock, cypherBlockSize, specificKey);
            // Add boot file: bt
            Files.write(fsp.getBoot(), btCiphered);
        }
    }

    private static short boolToShort(boolean b) {
        return (short) (b ? 1 : 0);
    }

    private static void writeActionNode(DataOutputStream liDos, int[] stageNodesIndexes) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(stageNodesIndexes.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int stageNodeIndex : stageNodesIndexes) {
            bb.putInt(stageNodeIndex);
        }
        liDos.write(bb.array());
        bb.clear();
    }

    private static String assetPathFromIndex(int index) {
        return String.format("000\\%08d", index);
    }

    private static byte[] computeSpecificKeyFromUUID(byte[] uuid) {
        byte[] btKey = XXTEACipher.cipherCommonKey(CipherMode.DECIPHER, uuid);
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
        try (InputStream is = classLoader.getResourceAsStream(relative)) {
            return is.readAllBytes();
        } catch (IOException e) {
            LOGGER.atError().withThrowable(e).log("Cannot load relative resource {}!", relative);
            return new byte[0];
        }
    }
}
