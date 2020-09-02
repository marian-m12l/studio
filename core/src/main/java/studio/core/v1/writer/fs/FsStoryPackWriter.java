/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.writer.fs;

import org.apache.commons.codec.digest.DigestUtils;
import studio.core.v1.model.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

/*
Writer for the new binary format coming with firmware 2.4
Assets must be prepared to match the expected format : 4-bits depth / RLE encoding BMP for images, and mono 44100Hz MP3 for sounds.
The first 512 bytes of most files are scrambled. This is worked around by copying scrambled parts and boot file from
an existing pack, and by avoiding reference to this part of the files when possible (using offsets). When not possible
(for the assets), the scrambled part is combined with the end of an asset to try and make a consistent file.
 */
public class FsStoryPackWriter {

    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String LIST_INDEX_FILENAME = "li";
    private static final String IMAGE_INDEX_FILENAME = "ri";
    private static final String IMAGE_FOLDER = "rf" + File.separator;
    private static final String SOUND_INDEX_FILENAME = "si";
    private static final String SOUND_FOLDER = "sf" + File.separator;
    private static final String BOOT_FILENAME = "bt";

    // FIXME These offsets exist to make sure the scrambled part of index files is never referenced
    private static final int INDEX_OFFSET_LI = 128;
    private static final int INDEX_OFFSET_RI = 43;
    private static final int INDEX_OFFSET_SI = 43;

    // FIXME Scrambled part of all files are extracted from an existing story pack
    private static final String EXISTING_PACK_PATH = System.getProperty("user.home") + "/.studio/fs/4CDF38C6";
    private static final String EXISTING_IMAGE_PATH = "/rf/000/CF3AFD3D";
    private static final String EXISTING_SOUND_PATH = "/sf/000/FD1684D3";

    // TODO Enriched metadata in a dedicated file (pack's title, description and thumbnail, nodes' name, group, type and position

    public void write(StoryPack pack, Path outputFolder) throws IOException {

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
        DataOutputStream liDos = new DataOutputStream(liFos);
        // Initialize file with the scrambled part
        initializeLiFile(liDos);
        // Add action nodes
        for (ActionNode actionNode : actionNodesOrdered) {
            writeActionNode(
                    liDos,
                    actionNode.getOptions().stream().mapToInt(stage -> pack.getStageNodes().indexOf(stage)).toArray()   // Each option points to a stage node by index in Nodes Index file (ni)
            );
        }
        liDos.close();
        liFos.close();


        // Add images index file: ri
        FileOutputStream riFos = new FileOutputStream(new File(packFolder, IMAGE_INDEX_FILENAME));
        DataOutputStream riDos = new DataOutputStream(riFos);
        // Initialize file with the scrambled part
        initializeRiFile(riDos);
        // Analyze the base image to determine how the actual image can be combined with the scrambled part
        BaseImageAnalysis analysis = analyzeBaseImageFile();
        // For each image asset: 12-bytes relative path (e.g. 000\11111111)
        for (int i=0; i<imageHashOrdered.size(); i++) {
            // Write image path into ri file
            String imageHash = imageHashOrdered.get(i);
            String rfPath = assetPathFromIndex(i);
            riDos.write(rfPath.getBytes(Charset.forName("UTF-8")));
            // Write image data into file
            File rfFile = new File(packFolder, IMAGE_FOLDER + rfPath.replace('\\', '/'));
            debug("Writing RF file: " + rfFile.toString());
            rfFile.getParentFile().mkdirs();
            FileOutputStream rfFos = new FileOutputStream(rfFile);
            writeImageAsset(rfFos, assets.get(imageHash), analysis);
            rfFos.close();
        }
        riDos.close();
        riFos.close();


        // Add sound index file: si
        FileOutputStream siFos = new FileOutputStream(new File(packFolder, "si"));
        DataOutputStream siDos = new DataOutputStream(siFos);
        // Initialize file with the scrambled part
        initializeSiFile(siDos);
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
            writeSoundAsset(sfFos, assets.get(audioHash));
            sfFos.close();
        }
        siDos.close();
        siFos.close();


        // Add boot file: bt
        FileOutputStream btFos = new FileOutputStream(new File(packFolder, BOOT_FILENAME));
        DataOutputStream btDos = new DataOutputStream(btFos);
        copyBootFile(btDos);
        btDos.close();
        btFos.close();
    }

    private static byte[] readFromExistingPack(String filePath, int length) throws IOException {
        // Open file, read <length>-bytes scrambled part
        FileInputStream fileInputStream = new FileInputStream(EXISTING_PACK_PATH + File.separator + filePath);
        byte[] scrambled = fileInputStream.readNBytes(length);
        fileInputStream.close();
        return scrambled;
    }

    // FIXME This is the "hackish" part, where we keep the scrambled part as-is. It will be ignored because all references to LI file will be offset by 128 (512 bytes)
    private static void initializeLiFile(DataOutputStream liDos) throws IOException {
        // Copy scrambled part from existing pack
        liDos.write(readFromExistingPack(LIST_INDEX_FILENAME, 512));
    }

    // FIXME This is the "hackish" part, where we keep the scrambled part as-is. It will be ignored because all references to RI file will be offset by 43 (516 bytes)
    private static void initializeRiFile(DataOutputStream riDos) throws IOException {
        // Copy scrambled part from existing pack
        riDos.write(readFromExistingPack(IMAGE_INDEX_FILENAME, 512));
        // Offset 4 more bytes, to be aligned with 12-bytes data chunks
        riDos.writeInt(0);
    }

    // FIXME This is the "hackish" part, where we keep the scrambled part as-is. It will be ignored because all references to SI file will be offset by 43 (516 bytes)
    private static void initializeSiFile(DataOutputStream siDos) throws IOException {
        // Copy scrambled part from existing pack
        siDos.write(readFromExistingPack(SOUND_INDEX_FILENAME, 512));
        // Offset 4 more bytes, to be aligned with 12-bytes data chunks
        siDos.writeInt(0);
    }

    // FIXME This is the "hackish" part, where we keep the boot file as-is.
    private static void copyBootFile(DataOutputStream btDos) throws IOException {
        // Copy boot file from existing pack
        btDos.write(readFromExistingPack(BOOT_FILENAME, 64));
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
        // All references to RI file must be offset by 43 (except -1)
        bb.putInt(imageAssetIndexInRI >= 0 ? imageAssetIndexInRI + INDEX_OFFSET_RI : imageAssetIndexInRI);
        // All references to SI file must be offset by 43 (except -1)
        bb.putInt(soundAssetIndexInSI >= 0 ? soundAssetIndexInSI + INDEX_OFFSET_SI : soundAssetIndexInSI);
        // All references to LI file must be offset by 128 (except -1)
        bb.putInt(okTransitionActionNodeIndexInLI >= 0 ? okTransitionActionNodeIndexInLI + INDEX_OFFSET_LI : okTransitionActionNodeIndexInLI);
        bb.putInt(okTransitionNumberOfOptions);
        bb.putInt(okTransitionSelectedOptionIndex);
        bb.putInt(homeTransitionActionNodeIndexInLI >= 0 ? homeTransitionActionNodeIndexInLI + INDEX_OFFSET_LI : homeTransitionActionNodeIndexInLI);
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


    /*
    Analysis of the original image file (from the existing pack) to determine how the actual image file wan be combined
    with the scrambled part:
      - Stores the position of the first clear / replaceable line
      - Counts the number of clear / replaceable lines
      - Stores the expected byte-size of those lines
      - Stores the scrambled part + all the clear data until the first clear / replaceable line
     */
    private static class BaseImageAnalysis {
        int firstReplaceableLine;
        int nbOfReplaceableLines;
        int expectedReplacementBytes;
        byte[] scrambledBytes;

        BaseImageAnalysis(int firstReplaceableLine, int nbOfReplaceableLines, int expectedReplacementBytes, byte[] scrambledBytes) {
            this.firstReplaceableLine = firstReplaceableLine;
            this.nbOfReplaceableLines = nbOfReplaceableLines;
            this.expectedReplacementBytes = expectedReplacementBytes;
            this.scrambledBytes = scrambledBytes;
        }
    }

    // Analysis of existing image file
    private static BaseImageAnalysis analyzeBaseImageFile() throws IOException {
        // Open existing image file
        FileInputStream fileInputStream = new FileInputStream(EXISTING_PACK_PATH + File.separator + EXISTING_IMAGE_PATH);
        // Store scrambled data and all the clear data until the first clear / replaceable line
        ByteArrayOutputStream scrambledBytes = new ByteArrayOutputStream();
        // Read scrambled part
        byte[] scrambled = fileInputStream.readNBytes(512);
        scrambledBytes.write(scrambled);

        int firstEOLIndex = -1;
        int nbOfReplaceableLines = 0;
        int index = 512;
        byte[] word = new byte[2];
        // Find first end-of-line (aligned 0x0000) after index 512
        while (fileInputStream.read(word) != -1) {
            // Keep all data until the first end-of-line
            if (firstEOLIndex == -1) {
                //debug(word[0] + " - " + word[1]);
                //debug("Store word: " + index);
                scrambledBytes.write(word);
            }
            // Is this an end-of-line word?
            if (word[0] == 0x00 && word[1] == 0x00) {
                if (firstEOLIndex == -1) {
                    // Keep first match
                    firstEOLIndex = index;
                } else {
                    // Count the other lines
                    //debug("Match EOL: " + index);
                    nbOfReplaceableLines++;
                }
            }
            // Is this the beginning of raw pixel data chunk (non-RLE-encoded)?
            else if (word[0] == 0x00 && word[1] != 0x01 && word[1] != 0x02) {
                // Skip the next N pixels
                int skipPixels = word[1] & 0xff;
                int skipBytes = (int) Math.ceil(skipPixels/2.0);
                if (skipBytes % 2 != 0) {
                    skipBytes++;
                }
                //debug("Skipping " + skipPixels + " uncompressed pixels (" + skipBytes + " bytes)");
                // If still in first line, keep the skipped data
                if (firstEOLIndex == -1) {
                    byte[] skipped = fileInputStream.readNBytes(skipBytes);
                    //debug("Store skipped bytes: " + skipBytes);
                    scrambledBytes.write(skipped);
                } else {
                    fileInputStream.skip(skipBytes);
                }
                index += skipBytes;
            }
            index += 2;
        }
        fileInputStream.close();

        int firstReplaceableLine = firstEOLIndex + 2;
        //debug("First match = " + firstEOLIndex);
        //debug("Count = " + nbOfReplaceableLines);
        //debug("Index = " + index);
        //debug("Expected = " + (index - firstReplaceableLine));
        return new BaseImageAnalysis(
                firstReplaceableLine,
                nbOfReplaceableLines,
                index - firstReplaceableLine,
                scrambledBytes.toByteArray()
        );
    }

    // Processing of an actual image file to combine it with the scrambled part
    private static byte[] preprocessImageFile(byte[] data, BaseImageAnalysis analysis) throws IOException {
        //debug("Preprocess: " + data.length + ": " + analysis.nbOfReplaceableLines + " - " + analysis.expectedReplacementBytes);

        // Read raw data index at 0x0A
        ByteBuffer wrap = ByteBuffer.wrap(data, 10, 4).order(ByteOrder.LITTLE_ENDIAN);
        int offset = wrap.getInt();
        //debug("Data offset = " + offset);

        int linesToSkip = 240 - analysis.nbOfReplaceableLines;
        int skippedLines = 0;
        int count = 0;
        int firstReplacementLineIndex = -1;
        int index = offset;
        // Skip <n> end-of-lines (aligned 0x0000) after data offset
        while (index < data.length-1) {
            // TODO Need to skip non-RLE chunks ?
            // Is this an end-of-line word?
            if (data[index] == 0x00 && data[index+1] == 0x00) {
                count++;
                if (skippedLines < linesToSkip) {
                    //debug("Skipping EOL: " + index);
                    skippedLines++;
                } else {
                    debug("Ignoring EOL: " + index);
                }
                if (skippedLines == linesToSkip) {
                    // Store index of the first replacement line
                    firstReplacementLineIndex = index+2;
                    //break;
                    skippedLines++;
                }
            }
            // Move to the next 2-bytes word
            index += 2;
        }

        //debug("EOL count: " + count);

        //debug("First replacement line at: " + firstReplacementLineIndex);
        //debug("Skipped = " + skippedLines);

        // Extract all bytes from firstReplacementLineIndex
        byte[] bytes = Arrays.copyOfRange(data, firstReplacementLineIndex, data.length);

        //debug("Expected replacement bytes = " + analysis.expectedReplacementBytes);
        //debug("Actual replacement bytes = " + bytes.length);

        // FIXME If the replacement lines are bigger than expected bytes, the image CANNOT BE COMBINED WITH SCRAMBLED PART
        if (bytes.length > analysis.expectedReplacementBytes) {
            throw new RuntimeException("Image file is too big to fit into base image: " + bytes.length + " > " + analysis.expectedReplacementBytes);
        }
        // If the replacement lines are smaller than expected bytes, expand some compressed chunks to match the expected bytes number
        else if (bytes.length < analysis.expectedReplacementBytes) {
            int diff = analysis.expectedReplacementBytes - bytes.length - 2; // Leave room to add missing EOL before end-of-file
            debug("Image file is too small: expanding compressed chunks to add the missing " + diff + " bytes");
            ByteArrayOutputStream expanded = new ByteArrayOutputStream();
            int i = 0;
            while (diff > 0) {
                // Uncompress chunk when both color indexes are identical
                int nbConsecutivePixels = bytes[i] & 0xff;
                byte firstColorIndex = (byte) ((bytes[i+1] & 0xf0) >> 4);
                byte secondColorIndex = (byte) (bytes[i+1] & 0x0f);
                //debug("\tAddress " + (i+firstReplacementLineIndex) + "\tPixels " + nbConsecutivePixels + "\tFirst " + firstColorIndex + "\tSecond " + secondColorIndex);
                if (nbConsecutivePixels > 2 && firstColorIndex == secondColorIndex) {
                    // Decompress N consecutive pixels (1 word) into N words: this add N-1 two-byte words
                    // Make sure to not expand more than necessary when just a few bytes are missing
                    int bytesToExpand = Math.min(diff, (nbConsecutivePixels-1)*2);

                    //debug("Decompressing chunk of " + nbConsecutivePixels + " consecutive pixels with color index " + firstColorIndex + " at address " + (i+firstReplacementLineIndex));

                    if (bytesToExpand == diff) {
                        //debug("This chunk exceeds remaining diff (" + (nbConsecutivePixels-1)*2 + " > " + diff + "): Adding only " + diff + " bytes");
                    }

                    // First word contains the remaining (not expanded) pixels
                    int remaining = nbConsecutivePixels - (bytesToExpand/2);
                    //debug("Remaining pixels in first chunk after expansion: " + remaining);
                    expanded.write(new byte[] { (byte)(remaining & 0xff), bytes[i+1] });
                    // Following words contain expanded pixels
                    //debug("Number of 1-PIXEL words added: " + (bytesToExpand/2));
                    for (int j = 0; j < bytesToExpand ; j+=2) {
                        expanded.write(new byte[] { 0x01, bytes[i+1] });
                    }

                    //debug("\tThis operation added" + bytesToExpand + " bytes");

                    diff -= bytesToExpand;
                } else {
                    expanded.write(bytes, i, 2);
                }
                i += 2;
            }
            // Copy as-is the remaining pixels
            expanded.write(bytes, i, bytes.length-i-2);
            // Add missing EOL before end-of-file
            expanded.write(new byte[] { 0x00, 0x00 });
            // End-of-file
            expanded.write(bytes, bytes.length-2, 2);
            bytes = expanded.toByteArray();
        } else {
            // Exact size, no need to process image file
            debug("WOW, the image is exactly the expected size!");
        }

        return bytes;
    }

    private static void writeImageAsset(FileOutputStream rfFos, byte[] data, BaseImageAnalysis analysis) throws IOException {
        // FIXME This is the "hackish" part, where we keep the scrambled part as-is. The BMP header and first data (lower rows) are kept, which means a part of the base image will be visible.
        // All data until the first replaceable line (including scrambled BMP header and first rows of pixel data are kept
        //debug("Scrambled bytes until first EOL = " + analysis.scrambledBytes.length);
        rfFos.write(analysis.scrambledBytes);
        // FIXME Using the lines number from analysis causes errors, which are avoided by using fewer replacement lines (e.g. 207 instead of 213)
        // FIXME This does not work for all images
        analysis.nbOfReplaceableLines = analysis.nbOfReplaceableLines - 6;
        // Image data must be the exact expected size, and contain the right amount of pixel data. To achieve this, smaller RLE-encoded pixel data are expanded to match the expected byte size and pixel rows
        byte[] imageReplacementBytes = preprocessImageFile(data, analysis);
        //debug("Replacement bytes = " + imageReplacementBytes.length);
        // Write the end of the image data
        rfFos.write(imageReplacementBytes);
    }

    private static void writeSoundAsset(FileOutputStream sfFos, byte[] data) throws IOException {
        // FIXME This is the "hackish" part, where we keep the scrambled part as-is. The MP3 header and first data (silence) are kept, which seems OK.
        // Copy scrambled part from existing pack
        sfFos.write(readFromExistingPack(EXISTING_SOUND_PATH, 512));
        // Write the end of the audio data
        sfFos.write(data, 512, data.length-512);
    }

    private static void debug(String message) {
        System.out.println(message);
    }

}
