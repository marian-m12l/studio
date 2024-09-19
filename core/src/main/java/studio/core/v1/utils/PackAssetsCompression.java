/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import org.apache.commons.codec.digest.DigestUtils;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.util.TreeMap;
import java.util.logging.Logger;

public class PackAssetsCompression {

    private static final Logger LOGGER = Logger.getLogger(PackAssetsCompression.class.getName());

    public static boolean hasCompressedAssets(StoryPack pack) {
        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);

            if (node.getImage() != null && !"image/bmp".equals(node.getImage().getMimeType())) {
                return true;
            }

            if (node.getAudio() != null && !"audio/x-wav".equals(node.getAudio().getMimeType())) {
                return true;
            }
        }

        return false;
    }

    public static StoryPack withCompressedAssets(StoryPack pack) throws Exception {
        // Store compressed assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();

        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);

            if (node.getImage() != null) {
                LOGGER.fine("Processing image asset `" + node.getImage().getName() + "`");
                byte[] imageData = node.getImage().getRawData();
                String assetHash = DigestUtils.sha1Hex(imageData);
                if (assets.get(assetHash) == null) {
                    if ("image/bmp".equals(node.getImage().getMimeType())) {
                        LOGGER.fine("Compressing BMP image asset `" + node.getImage().getName() + "` into PNG");
                        imageData = ImageConversion.bitmapToPng(imageData);
                    }
                    assets.put(assetHash, imageData);
                }
                // Use asset (already compressed) bytes from map
                node.getImage().setRawData(assets.get(assetHash));
                if ("image/bmp".equals(node.getImage().getMimeType())) {
                    node.getImage().setMimeType("image/png");
                }
            }

            if (node.getAudio() != null) {
                LOGGER.fine("Processing audio asset `" + node.getAudio().getName() + "`");
                byte[] audioData = node.getAudio().getRawData();
                String assetHash = DigestUtils.sha1Hex(audioData);
                if (assets.get(assetHash) == null) {
                    if ("audio/x-wav".equals(node.getAudio().getMimeType())) {
                        LOGGER.fine("Compressing WAV audio asset `" + node.getAudio().getName() + "` into OGG");
                        audioData = AudioConversion.waveToOgg(audioData);
                        node.getAudio().setMimeType("audio/ogg");
                    }
                    assets.put(assetHash, audioData);
                }
                // Use asset (already compressed) bytes from map
                node.getAudio().setRawData(assets.get(assetHash));
                if ("audio/x-wav".equals(node.getAudio().getMimeType())) {
                    node.getAudio().setMimeType("audio/ogg");
                }
            }
        }

        return pack;
    }

    public static StoryPack withUncompressedAssets(StoryPack pack) throws Exception {
        // Store uncompressed assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();

        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);

            if (node.getImage() != null) {
                LOGGER.fine("Processing image asset `" + node.getImage().getName() + "`");
                byte[] imageData = node.getImage().getRawData();
                String assetHash = DigestUtils.sha1Hex(imageData);
                if (assets.get(assetHash) == null) {
                    switch (node.getImage().getMimeType()) {
                        case "image/png":
                            LOGGER.fine("Uncompressing PNG image asset `" + node.getImage().getName() + "` into BMP");
                            imageData = ImageConversion.anyToBitmap(imageData);
                            break;
                        case "image/jpeg":
                            LOGGER.fine("Uncompressing JPG image asset `" + node.getImage().getName() + "` into BMP");
                            imageData = ImageConversion.anyToBitmap(imageData);
                            break;
                        case "image/bmp":
                            // Convert from 4-bits depth / RLE encoding BMP
                            if (imageData[28] == 0x04 && imageData[30] == 0x02) {
                                LOGGER.fine("Uncompressing 4-bits/RLE BMP image asset `" + node.getImage().getName() + "` into BMP");
                                imageData = ImageConversion.anyToBitmap(imageData);
                            }
                            break;
                    }
                    assets.put(assetHash, imageData);
                }
                // Use asset (already uncompressed) bytes from map
                node.getImage().setRawData(assets.get(assetHash));
                node.getImage().setMimeType("image/bmp");
            }

            if (node.getAudio() != null) {
                LOGGER.fine("Processing audio asset `" + node.getAudio().getName() + "`");
                byte[] audioData = node.getAudio().getRawData();
                String assetHash = DigestUtils.sha1Hex(audioData);
                if (assets.get(assetHash) == null) {
                    if (!"audio/x-wav".equals(node.getAudio().getMimeType())) {
                        switch (node.getAudio().getMimeType()) {
                            case "audio/ogg":
                                LOGGER.fine("Uncompressing OGG audio asset `" + node.getAudio().getName() + "` into WAV");
                                audioData = AudioConversion.oggToWave(audioData);
                                break;
                            case "audio/mpeg":
                                LOGGER.fine("Uncompressing MP3 audio asset `" + node.getAudio().getName() + "` into WAV");
                                audioData = AudioConversion.mp3ToWave(audioData);
                                break;
                        }
                    }
                    assets.put(assetHash, audioData);
                }
                // Use asset (already uncompressed) bytes from map
                node.getAudio().setRawData(assets.get(assetHash));
                node.getAudio().setMimeType("audio/x-wav");
            }
        }

        return pack;
    }

    public static StoryPack withPreparedAssetsFirmware2dot4(StoryPack pack) throws Exception {
        // Store prepared assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();

        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);

            if (node.getImage() != null) {
                LOGGER.fine("Processing image asset `" + node.getImage().getName() + "`");
                byte[] imageData = node.getImage().getRawData();
                String assetHash = DigestUtils.sha1Hex(imageData);
                if (assets.get(assetHash) == null) {
                    // Convert to 4-bits depth / RLE encoding BMP
                    if (!"image/bmp".equals(node.getImage().getMimeType()) || imageData[28] != 0x04 || imageData[30] != 0x02) {
                        LOGGER.fine("Converting image asset `" + node.getImage().getName() + "` into 4-bits/RLE BMP");
                        imageData = ImageConversion.anyToRLECompressedBitmap(imageData);
                    }
                    assets.put(assetHash, imageData);
                }
                // Use asset (already compressed) bytes from map
                node.getImage().setRawData(assets.get(assetHash));
                node.getImage().setMimeType("image/bmp");
            }

            if (node.getAudio() != null) {
                LOGGER.fine("Processing audio asset `" + node.getAudio().getName() + "`");
                byte[] audioData = node.getAudio().getRawData();
                String assetHash = DigestUtils.sha1Hex(audioData);
                if (assets.get(assetHash) == null) {
                    if (!"audio/mp3".equals(node.getAudio().getMimeType()) && !"audio/mpeg".equals(node.getAudio().getMimeType())) {
                        LOGGER.fine("Converting audio asset `" + node.getAudio().getName() + "` into MP3");
                        audioData = AudioConversion.anyToMp3(audioData);
                    } else {
                        // Remove potential ID3 tags
                        audioData = ID3Tags.removeID3v1Tag(audioData);
                        audioData = ID3Tags.removeID3v2Tag(audioData);
                        // Check that the file is MONO / 44100Hz
                        AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(audioData));
                        if (audioFileFormat.getFormat().getChannels() != AudioConversion.CHANNELS
                                || audioFileFormat.getFormat().getSampleRate() != AudioConversion.MP3_SAMPLE_RATE) {
                            LOGGER.fine("Re-encoding MP3 audio asset `" + node.getAudio().getName() + "`");
                            audioData = AudioConversion.anyToMp3(audioData);
                        }
                    }
                    assets.put(assetHash, audioData);
                }
                // Use asset (already compressed) bytes from map
                node.getAudio().setRawData(assets.get(assetHash));
                node.getAudio().setMimeType("audio/mpeg");
            }
        }

        return pack;
    }
}
