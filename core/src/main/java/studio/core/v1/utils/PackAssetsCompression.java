/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import java.io.ByteArrayInputStream;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.codec.digest.DigestUtils;

import studio.core.v1.model.AudioAsset;
import studio.core.v1.model.ImageAsset;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.mime.AudioType;
import studio.core.v1.model.mime.ImageType;

public class PackAssetsCompression {

    private static final Logger LOGGER = Logger.getLogger(PackAssetsCompression.class.getName());

    private PackAssetsCompression() {
        throw new IllegalStateException("Utility class");
    }
    
    public static boolean hasCompressedAssets(StoryPack pack) {
        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);
            if (node.getImage() != null && !ImageType.BMP.is(node.getImage().getMimeType())) {
                return true;
            }
            if (node.getAudio() != null && !AudioType.WAV.is(node.getAudio().getMimeType())) {
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

            ImageAsset ia = node.getImage(); 
            if (ia != null) {
                byte[] imageData = ia.getRawData();
                String assetHash = DigestUtils.sha1Hex(imageData);
                if (!assets.containsKey(assetHash)) {
                    if (ImageType.BMP.is(ia.getMimeType())) {
                        LOGGER.fine("Compressing BMP image asset into PNG");
                        imageData = ImageConversion.bitmapToPng(imageData);
                    }
                    assets.put(assetHash, imageData);
                }
                // Use asset (already compressed) bytes from map
                ia.setRawData(assets.get(assetHash));
                if (ImageType.BMP.is(ia.getMimeType())) {
                    ia.setMimeType(ImageType.PNG.getMime());
                }
            }

            AudioAsset aa = node.getAudio(); 
            if (aa != null) {
                byte[] audioData = aa.getRawData();
                String assetHash = DigestUtils.sha1Hex(audioData);
                if (!assets.containsKey(assetHash)) {
                    if (AudioType.WAV.is(aa.getMimeType())) {
                        LOGGER.fine("Compressing WAV audio asset into OGG");
                        audioData = AudioConversion.waveToOgg(audioData);
                        aa.setMimeType(AudioType.OGG.getMime());
                    }
                    assets.put(assetHash, audioData);
                }
                // Use asset (already compressed) bytes from map
                aa.setRawData(assets.get(assetHash));
                if (AudioType.WAV.is(aa.getMimeType())) {
                    aa.setMimeType(AudioType.OGG.getMime());
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

            ImageAsset ia = node.getImage();
            if (ia != null) {
                byte[] imageData = ia.getRawData();
                String assetHash = DigestUtils.sha1Hex(imageData);
                if (!assets.containsKey(assetHash)) {
                    // Convert from 4-bits depth / RLE encoding BMP
                    if (ImageType.BMP.is(ia.getMimeType()) && imageData[28] == 0x04 && imageData[30] == 0x02) {
                        LOGGER.fine("Uncompressing 4-bits/RLE BMP image asset into BMP");
                        imageData = ImageConversion.anyToBitmap(imageData);
                    }
                    if (ImageType.JPEG.is(ia.getMimeType())) {
                        LOGGER.fine("Uncompressing JPG image asset into BMP");
                        imageData = ImageConversion.anyToBitmap(imageData);
                    }
                    if (ImageType.PNG.is(ia.getMimeType())) {
                        LOGGER.fine("Uncompressing PNG image asset into BMP");
                        imageData = ImageConversion.anyToBitmap(imageData);
                    }
                    assets.put(assetHash, imageData);
                }
                // Use asset (already uncompressed) bytes from map
                ia.setRawData(assets.get(assetHash));
                ia.setMimeType(ImageType.BMP.getMime());
            }

            AudioAsset aa = node.getAudio();
            if (aa != null) {
                byte[] audioData = aa.getRawData();
                String assetHash = DigestUtils.sha1Hex(audioData);
                if (!assets.containsKey(assetHash)) {
                    if (AudioType.OGG.is(aa.getMimeType())) {
                        LOGGER.fine("Uncompressing OGG audio asset into WAV");
                        audioData = AudioConversion.oggToWave(audioData);
                    }
                    if (AudioType.MPEG.is(aa.getMimeType())) {
                        LOGGER.fine("Uncompressing MP3 audio asset into WAV");
                        audioData = AudioConversion.mp3ToWave(audioData);
                    }
                    // Nothing for MimeType.AUDIO_WAV
                    assets.put(assetHash, audioData);
                }
                // Use asset (already uncompressed) bytes from map
                aa.setRawData(assets.get(assetHash));
                aa.setMimeType(AudioType.WAV.getMime());
            }
        }

        return pack;
    }

    public static StoryPack withPreparedAssetsFirmware2dot4(StoryPack pack) throws Exception {
        // Store prepared assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();

        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);

            ImageAsset ia = node.getImage();
            if (ia != null) {
                byte[] imageData = ia.getRawData();
                String assetHash = DigestUtils.sha1Hex(imageData);
                if (!assets.containsKey(assetHash)) {
                    // Convert to 4-bits depth / RLE encoding BMP
                    if (!ImageType.BMP.is(ia.getMimeType()) || imageData[28] != 0x04 || imageData[30] != 0x02) {
                        LOGGER.fine("Converting image asset into 4-bits/RLE BMP");
                        imageData = ImageConversion.anyToRLECompressedBitmap(imageData);
                    }
                    assets.put(assetHash, imageData);
                }
                // Use asset (already compressed) bytes from map
                ia.setRawData(assets.get(assetHash));
                ia.setMimeType(ImageType.BMP.getMime());
            }

            AudioAsset aa = node.getAudio();
            if (aa != null) {
                byte[] audioData = aa.getRawData();
                String assetHash = DigestUtils.sha1Hex(audioData);
                if (!assets.containsKey(assetHash)) {
                    if (!AudioType.MPEG.is(aa.getMimeType()) && !AudioType.MP3.is(aa.getMimeType())) {
                        LOGGER.fine("Converting audio asset into MP3");
                        audioData = AudioConversion.anyToMp3(audioData);
                    } else {
                        // Remove potential ID3 tags
                        audioData = ID3Tags.removeID3v1Tag(audioData);
                        audioData = ID3Tags.removeID3v2Tag(audioData);
                        // Check that the file is MONO / 44100Hz
                        AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(audioData));
                        if (audioFileFormat.getFormat().getChannels() != AudioConversion.CHANNELS
                                || audioFileFormat.getFormat().getSampleRate() != AudioConversion.MP3_SAMPLE_RATE) {
                            LOGGER.fine("Re-encoding MP3 audio asset");
                            audioData = AudioConversion.anyToMp3(audioData);
                        }
                    }
                    assets.put(assetHash, audioData);
                }
                // Use asset (already compressed) bytes from map
                aa.setRawData(assets.get(assetHash));
                aa.setMimeType(AudioType.MPEG.getMime());
            }
        }

        return pack;
    }
}
