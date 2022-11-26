/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.core.v1.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.asset.MediaAsset;
import studio.core.v1.model.asset.MediaAssetType;
import studio.core.v1.utils.audio.AudioConversion;
import studio.core.v1.utils.audio.ID3Tags;
import studio.core.v1.utils.image.ImageConversion;
import studio.core.v1.utils.stream.StoppingConsumer;
import studio.core.v1.utils.stream.ThrowingFunction;

public class StoryPackConverter {

    private static final Logger LOGGER = LogManager.getLogger(StoryPackConverter.class);

    private Path libraryPath;
    private Path tmpDirPath;

    public StoryPackConverter(Path libraryPath, Path tmpDirPath) {
        this.libraryPath = libraryPath;
        this.tmpDirPath = tmpDirPath;
    }

    public Path convert(String packName, PackFormat outFormat, boolean allowEnriched) {
        Path packPath = libraryPath.resolve(packName);
        PackFormat inFormat = PackFormat.fromPath(packPath);
        LOGGER.info("Pack is in {} format. Converting to {} format", inFormat, outFormat);
        // check formats
        if (inFormat == outFormat) {
            throw new StoryTellerException("Pack is already in " + outFormat + " format : " + packPath.getFileName());
        }
        try {
            // Read pack
            LOGGER.info("Reading {} format pack", inFormat);
            StoryPack storyPack = inFormat.getReader().read(packPath);
            // Convert
            switch (outFormat) {
            case ARCHIVE:
                // Compress pack assets
                if (inFormat == PackFormat.RAW) {
                    LOGGER.info("Compressing pack assets");
                    processCompressed(storyPack);
                }
                // force enriched pack
                allowEnriched = true;
                break;
            case FS:
                // Prepare assets (RLE-encoded BMP, audio must already be MP3)
                LOGGER.info("Converting assets if necessary");
                processFirmware2dot4(storyPack);
                // force enriched pack
                allowEnriched = true;
                break;
            case RAW:
                // Uncompress pack assets
                if (hasCompressedAssets(storyPack)) {
                    LOGGER.info("Uncompressing pack assets");
                    processUncompressed(storyPack);
                }
                break;
            }

            // Write to temporary dir
            String destName = storyPack.getUuid() + ".converted_" + System.currentTimeMillis()
                    + outFormat.getExtension();
            Path tmpPath = tmpDirPath.resolve(destName);
            LOGGER.info("Writing {} format pack, using temporary : {}", outFormat, tmpPath);
            outFormat.getWriter().write(storyPack, tmpPath, allowEnriched);

            // Move to library
            Path destPath = libraryPath.resolve(destName);
            LOGGER.info("Moving {} format pack into local library: {}", outFormat, destPath);
            return Files.move(tmpPath, destPath);
        } catch (IOException e) {
            throw new StoryTellerException("Failed to convert " + inFormat + " pack to " + outFormat, e);
        }
    }

    public static boolean hasCompressedAssets(StoryPack storyPack) {
        for (StageNode node : storyPack.getStageNodes()) {
            if ((node.getImage() != null && MediaAssetType.BMP != node.getImage().getType()) || (node.getAudio() != null && MediaAssetType.WAV != node.getAudio().getType())) {
                return true;
            }
        }
        return false;
    }

    private static void processCompressed(StoryPack storyPack) {
        // Image
        processImageAssets(storyPack, MediaAssetType.PNG, ThrowingFunction.unchecked(ia -> {
            byte[] imageData = ia.getRawData();
            if (MediaAssetType.BMP == ia.getType()) {
                LOGGER.debug("Compressing BMP image asset into PNG");
                imageData = ImageConversion.bitmapToPng(imageData);
            }
            return imageData;
        }));
        // Audio
        processAudioAssets(storyPack, MediaAssetType.OGG, ThrowingFunction.unchecked(aa -> {
            byte[] audioData = aa.getRawData();
            if (MediaAssetType.WAV == aa.getType()) {
                LOGGER.debug("Compressing WAV audio asset into OGG");
                audioData = AudioConversion.waveToOgg(audioData);
            }
            return audioData;
        }));
    }

    private static void processUncompressed(StoryPack storyPack) {
        // Image
        processImageAssets(storyPack, MediaAssetType.BMP, ThrowingFunction.unchecked(ia -> {
            byte[] imageData = ia.getRawData();
            // Convert from 4-bits depth / RLE encoding BMP
            if (MediaAssetType.BMP == ia.getType() && imageData[28] == 0x04 && imageData[30] == 0x02) {
                LOGGER.debug("Uncompressing 4-bits/RLE BMP image asset into BMP");
                imageData = ImageConversion.anyToBitmap(imageData);
            }
            if (MediaAssetType.JPEG == ia.getType() || MediaAssetType.PNG == ia.getType()) {
                LOGGER.debug("Uncompressing {} image asset into BMP", ia.getType());
                imageData = ImageConversion.anyToBitmap(imageData);
            }
            return imageData;
        }));
        // Audio
        processAudioAssets(storyPack, MediaAssetType.WAV, ThrowingFunction.unchecked(aa -> {
            byte[] audioData = aa.getRawData();
            if (MediaAssetType.OGG == aa.getType()) {
                LOGGER.debug("Uncompressing OGG audio asset into WAV");
                audioData = AudioConversion.oggToWave(audioData);
            }
            if (MediaAssetType.MP3 == aa.getType()) {
                LOGGER.debug("Uncompressing MP3 audio asset into WAV");
                audioData = AudioConversion.mp3ToWave(audioData);
            }
            return audioData;
        }));
    }

    private static void processFirmware2dot4(StoryPack storyPack) {
        // Image
        processImageAssets(storyPack, MediaAssetType.BMP, ThrowingFunction.unchecked(ia -> {
            byte[] imageData = ia.getRawData();
            // Convert to 4-bits depth / RLE encoding BMP
            if (MediaAssetType.BMP != ia.getType() || imageData[28] != 0x04 || imageData[30] != 0x02) {
                LOGGER.debug("Converting image asset into 4-bits/RLE BMP");
                imageData = ImageConversion.anyToRLECompressedBitmap(imageData);
            }
            return imageData;
        }));
        // Audio
        processAudioAssets(storyPack, MediaAssetType.MP3, ThrowingFunction.unchecked(aa -> {
            byte[] audioData = aa.getRawData();
            if (MediaAssetType.MP3 != aa.getType()) {
                LOGGER.debug("Converting audio asset into MP3");
                audioData = AudioConversion.anyToMp3(audioData);
            } else {
                // Remove potential ID3 tags
                audioData = ID3Tags.removeID3v1Tag(audioData);
                audioData = ID3Tags.removeID3v2Tag(audioData);
                // Check that the file is MONO / 44100Hz
                try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData)) {
                    AudioFormat audioFormat = AudioSystem.getAudioFileFormat(bais).getFormat();
                    if (audioFormat.getChannels() != AudioConversion.CHANNELS
                            || audioFormat.getSampleRate() != AudioConversion.MP3_SAMPLE_RATE) {
                        LOGGER.debug("Re-encoding MP3 audio asset");
                        audioData = AudioConversion.anyToMp3(audioData);
                    }
                }
            }
            return audioData;
        }));
    }

    private enum MediaGroup {
       AUDIO, IMAGE;
    }

    private static void processImageAssets(StoryPack storyPack, MediaAssetType targetType,
            Function<MediaAsset, byte[]> imageProcessor) {
       processAssets(MediaGroup.IMAGE, storyPack, targetType, imageProcessor);
    }

    private static void processAudioAssets(StoryPack storyPack, MediaAssetType targetType,
            Function<MediaAsset, byte[]> audioProcessor) {
       processAssets(MediaGroup.AUDIO, storyPack, targetType, audioProcessor);
    }

    private static void processAssets(MediaGroup mg, StoryPack storyPack, MediaAssetType targetType,
            Function<MediaAsset, byte[]> processor) {
        // Cache prepared assets bytes
        Map<String, byte[]> assets = new ConcurrentHashMap<>();
        List<MediaAsset> medias = storyPack.assets(mg == MediaGroup.IMAGE);
        AtomicInteger i = new AtomicInteger(0);

        // Multi-threaded processing
        medias.parallelStream().forEach(StoppingConsumer.stopped(a -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{} from node {}/{} [{}]", mg, i.incrementAndGet(), medias.size(),
                        Thread.currentThread().getName());
            }
            String assetHash = a.findHash();
            // Update data (converted if needed)
            a.setRawData(assets.computeIfAbsent(assetHash, s -> processor.apply(a)));
            // force type
            a.setType(targetType);
        }));
        // Clean cache
        assets.clear();
    }
}
