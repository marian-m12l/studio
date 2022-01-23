/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import studio.core.v1.model.AudioAsset;
import studio.core.v1.model.ImageAsset;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.mime.AudioType;
import studio.core.v1.model.mime.ImageType;
import studio.core.v1.utils.stream.ThrowingFunction;

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

    public static void processCompressed(StoryPack pack) {
        // Image
        processImageAssets(pack, ImageType.PNG, ThrowingFunction.unchecked(ia -> {
            byte[] imageData = ia.getRawData();
            if (ImageType.BMP.is(ia.getMimeType())) {
                LOGGER.fine("Compressing BMP image asset into PNG");
                imageData = ImageConversion.bitmapToPng(imageData);
            }
            return imageData;
        }));
        // Audio
        processAudioAssets(pack, AudioType.OGG, ThrowingFunction.unchecked(aa -> {
            byte[] audioData = aa.getRawData();
            if (AudioType.WAV.is(aa.getMimeType())) {
                LOGGER.fine("Compressing WAV audio asset into OGG");
                audioData = AudioConversion.waveToOgg(audioData);
            }
            return audioData;
        }));
    }

    public static void processUncompressed(StoryPack pack) {
        // Image
        processImageAssets(pack, ImageType.BMP, ThrowingFunction.unchecked(ia -> {
            byte[] imageData = ia.getRawData();
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
            return imageData;
        }));
        // Audio
        processAudioAssets(pack, AudioType.WAV, ThrowingFunction.unchecked(aa -> {
            byte[] audioData = aa.getRawData();
            if (AudioType.OGG.is(aa.getMimeType())) {
                LOGGER.fine("Uncompressing OGG audio asset into WAV");
                audioData = AudioConversion.oggToWave(audioData);
            }
            if (AudioType.MPEG.is(aa.getMimeType())) {
                LOGGER.fine("Uncompressing MP3 audio asset into WAV");
                audioData = AudioConversion.mp3ToWave(audioData);
            }
            return audioData;
        }));
    }

    public static void processFirmware2dot4(StoryPack pack) {
        // Image
        processImageAssets(pack, ImageType.BMP, ThrowingFunction.unchecked(ia -> {
            byte[] imageData = ia.getRawData();
            // Convert to 4-bits depth / RLE encoding BMP
            if (!ImageType.BMP.is(ia.getMimeType()) || imageData[28] != 0x04 || imageData[30] != 0x02) {
                LOGGER.fine("Converting image asset into 4-bits/RLE BMP");
                imageData = ImageConversion.anyToRLECompressedBitmap(imageData);
            }
            return imageData;
        }));
        // Audio
        processAudioAssets(pack, AudioType.MPEG, ThrowingFunction.unchecked(aa -> {
            byte[] audioData = aa.getRawData();
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
            return audioData;
        }));
    }

    private static void processImageAssets(StoryPack pack, ImageType targetType,
            Function<ImageAsset, byte[]> imageProcessor) {
        // Cache prepared assets bytes
        Map<String, byte[]> assets = new ConcurrentHashMap<>();
        int nbNodes = pack.getStageNodes().size();
        AtomicInteger i = new AtomicInteger(0);

        // Multi-threaded processing : images
        pack.getStageNodes().parallelStream().forEach(node -> {
            LOGGER.fine("Image from node " + i.incrementAndGet() + "/" + nbNodes);
            ImageAsset ia = node.getImage();
            if (ia != null) {
                byte[] imageData = ia.getRawData();
                String assetHash = SecurityUtils.sha1Hex(imageData);
                if (!assets.containsKey(assetHash)) {
                    // actual conversion
                    imageData = imageProcessor.apply(ia);
                    assets.put(assetHash, imageData);
                }
                // Use asset (already compressed) bytes from map
                ia.setRawData(assets.get(assetHash));
                ia.setMimeType(targetType.getMime());
            }
        });
        // Clean cache
        assets.clear();
    }

    private static void processAudioAssets(StoryPack pack, AudioType targetType,
            Function<AudioAsset, byte[]> audioProcessor) {
        // Cache prepared assets bytes
        Map<String, byte[]> assets = new ConcurrentHashMap<>();
        int nbNodes = pack.getStageNodes().size();
        AtomicInteger i = new AtomicInteger(0);

        // Multi-threaded processing : audio
        pack.getStageNodes().parallelStream().forEach(node -> {
            LOGGER.fine("Audio from node " + i.incrementAndGet() + "/" + nbNodes);
            AudioAsset aa = node.getAudio();
            if (aa != null) {
                byte[] audioData = aa.getRawData();
                String assetHash = SecurityUtils.sha1Hex(audioData);
                if (!assets.containsKey(assetHash)) {
                    // actual conversion
                    audioData = audioProcessor.apply(aa);
                    assets.put(assetHash, audioData);
                }
                // Use asset (already compressed) bytes from map
                aa.setRawData(assets.get(assetHash));
                aa.setMimeType(targetType.getMime());
            }
        });
        // Clean cache
        assets.clear();
    }
}
