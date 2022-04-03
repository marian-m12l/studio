/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import static studio.core.v1.writer.fs.FsStoryPackWriter.transformUuid;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.usb4java.Device;

import studio.core.v1.utils.SecurityUtils;
import studio.core.v1.utils.exception.StoryTellerException;
import studio.core.v1.writer.fs.FsStoryPackWriter;
import studio.driver.DeviceVersion;
import studio.driver.LibUsbDetectionHelper;
import studio.driver.StoryTellerAsyncDriver;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.TransferStatus;
import studio.driver.model.fs.FsDeviceInfos;
import studio.driver.model.fs.FsStoryPackInfos;

public class FsStoryTellerAsyncDriver implements StoryTellerAsyncDriver<FsDeviceInfos, FsStoryPackInfos> {

    private static final Logger LOGGER = LogManager.getLogger(FsStoryTellerAsyncDriver.class);

    private static final String DEVICE_METADATA_FILENAME = ".md";
    private static final String PACK_INDEX_FILENAME = ".pi";
    private static final String CONTENT_FOLDER = ".content";
    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String NIGHT_MODE_FILENAME = "nm";

    private static final long FS_MOUNTPOINT_POLL_DELAY = 1000L;
    private static final long FS_MOUNTPOINT_RETRY = 10;


    private Device device = null;
    private Path partitionMountPoint = null;
    private List<DevicePluggedListener> pluggedlisteners = new ArrayList<>();
    private List<DeviceUnpluggedListener> unpluggedlisteners = new ArrayList<>();

    public FsStoryTellerAsyncDriver() {
        // Initialize libusb, handle and propagate hotplug events
        LOGGER.debug("Registering hotplug listener");
        LibUsbDetectionHelper.initializeLibUsb(DeviceVersion.DEVICE_VERSION_2, //
                device2 -> {
                    // Wait for a partition to be mounted which contains the .md file
                    LOGGER.debug("Waiting for device partition...");
                    for (int i = 0; i < FS_MOUNTPOINT_RETRY && partitionMountPoint == null; i++) {
                        try {
                            Thread.sleep(FS_MOUNTPOINT_POLL_DELAY);
                            DeviceUtils.listMountPoints().forEach(path -> {
                                LOGGER.trace("Looking for .md in {}", path);
                                if (Files.exists(path.resolve(DEVICE_METADATA_FILENAME))) {
                                    partitionMountPoint = path;
                                    LOGGER.info("FS device partition located: {}", partitionMountPoint);
                                }
                            });
                        } catch (InterruptedException e) {
                            LOGGER.error("Failed to locate device partition", e);
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (partitionMountPoint == null) {
                        throw new StoryTellerException("Could not locate device partition");
                    }
                    // Update device reference
                    FsStoryTellerAsyncDriver.this.device = device2;
                    // Notify listeners
                    FsStoryTellerAsyncDriver.this.pluggedlisteners.forEach(l -> l.onDevicePlugged(device2));
                }, //
                device2 -> {
                    // Update device reference
                    FsStoryTellerAsyncDriver.this.device = null;
                    FsStoryTellerAsyncDriver.this.partitionMountPoint = null;
                    // Notify listeners
                    FsStoryTellerAsyncDriver.this.unpluggedlisteners.forEach(l -> l.onDeviceUnplugged(device2));
                });
    }

    public void registerDeviceListener(DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener) {
        this.pluggedlisteners.add(pluggedlistener);
        this.unpluggedlisteners.add(unpluggedlistener);
        if (this.device != null) {
            pluggedlistener.onDevicePlugged(this.device);
        }
    }

    public CompletionStage<FsDeviceInfos> getDeviceInfos() {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(noDevicePluggedException());
        }
        FsDeviceInfos infos = new FsDeviceInfos();
        Path mdFile = this.partitionMountPoint.resolve(DEVICE_METADATA_FILENAME);
        LOGGER.trace("Reading device infos from file: {}", mdFile);

        try(InputStream deviceMetadataFis = new BufferedInputStream(Files.newInputStream(mdFile))) {
            // MD file format version
            short mdVersion = readLittleEndianShort(deviceMetadataFis);
            LOGGER.trace("Device metadata format version: {}", mdVersion);
            if (mdVersion < 1 || mdVersion > 3) {
                return CompletableFuture.failedFuture(new StoryTellerException("Unsupported device metadata format version: " + mdVersion));
            }

            // Firmware version
            deviceMetadataFis.skip(4);
            short major = readLittleEndianShort(deviceMetadataFis);
            short minor = readLittleEndianShort(deviceMetadataFis);
            infos.setFirmwareMajor(major);
            infos.setFirmwareMinor(minor);
            LOGGER.debug("Firmware version: {}.{}", major, minor);

            // Serial number
            String serialNumber = null;
            long sn = readBigEndianLong(deviceMetadataFis);
            if (sn != 0L && sn != -1L && sn != -4294967296L) {
                serialNumber = String.format("%014d", sn);
                LOGGER.debug("Serial Number: {}", serialNumber);
            } else {
                LOGGER.warn("No serial number in SPI");
            }
            infos.setSerialNumber(serialNumber);

            // UUID
            deviceMetadataFis.skip(238);
            byte[] deviceId = deviceMetadataFis.readNBytes(256);
            infos.setDeviceId(deviceId);
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("UUID: {}", SecurityUtils.encodeHex(deviceId));
            }

            // SD card size and used space
            FileStore mdFd = Files.getFileStore(mdFile); 
            long sdCardTotalSpace = mdFd.getTotalSpace();
            long sdCardUsedSpace = mdFd.getTotalSpace() - mdFd.getUnallocatedSpace();
            double percent = Math.round(100d * 100d * sdCardUsedSpace / sdCardTotalSpace) / 100d;
            infos.setSdCardSizeInBytes(sdCardTotalSpace);
            infos.setUsedSpaceInBytes(sdCardUsedSpace);
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("SD card used : {}% ({} / {})", percent, FileUtils.readableByteSize(sdCardUsedSpace), FileUtils.readableByteSize(sdCardTotalSpace) );
            }
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new StoryTellerException("Failed to read device metadata on partition", e));
        }

        return CompletableFuture.completedFuture(infos);
    }

    private short readLittleEndianShort(InputStream fis) throws IOException {
        byte[] buffer = new byte[2];
        fis.read(buffer);
        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        return bb.getShort();
    }

    private long readBigEndianLong(InputStream fis) throws IOException {
        byte[] buffer = new byte[8];
        fis.read(buffer);
        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
        return bb.getLong();
    }


    public CompletionStage<List<FsStoryPackInfos>> getPacksList() {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(noDevicePluggedException());
        }

        return readPackIndex()
                .thenApply(packUUIDs -> {
                    try {
                        LOGGER.debug("Number of packs in index: {}", packUUIDs.size());
                        List<FsStoryPackInfos> packs = new ArrayList<>();
                        for (UUID packUUID : packUUIDs) {
                            FsStoryPackInfos packInfos = new FsStoryPackInfos();
                            packInfos.setUuid(packUUID);
                            LOGGER.debug("Pack UUID: {}", packUUID);

                            // Compute .content folder (last 4 bytes of UUID)
                            String folderName = transformUuid(packUUID.toString());
                            Path packPath = this.partitionMountPoint.resolve(CONTENT_FOLDER).resolve(folderName);
                            packInfos.setFolderName(folderName);

                            // Open 'ni' file
                            Path niPath = packPath.resolve(NODE_INDEX_FILENAME);
                            try(DataInputStream niDis = new DataInputStream(new BufferedInputStream(Files.newInputStream(niPath)))) {
                                ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
                                short version = bb.getShort(2);
                                packInfos.setVersion(version);
                                LOGGER.debug("Pack version: {}", version);
                            }

                            // Night mode is available if file 'nm' exists
                            packInfos.setNightModeAvailable(Files.exists(packPath.resolve(NIGHT_MODE_FILENAME)));

                            // Compute folder size
                            packInfos.setSizeInBytes(FileUtils.getFolderSize(packPath));

                            packs.add(packInfos);
                        }
                        return packs;
                    } catch (Exception e) {
                        throw new StoryTellerException("Failed to read pack metadata on device partition", e);
                    }
                });
    }

    private CompletionStage<List<UUID>> readPackIndex() {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = new ArrayList<>();
            Path piFile = this.partitionMountPoint.resolve(PACK_INDEX_FILENAME);
            LOGGER.trace("Reading packs index from file: {}", piFile);
            try {
                ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(piFile));
                while (bb.hasRemaining()) {
                    long high = bb.getLong();
                    long low = bb.getLong();
                    packUUIDs.add(new UUID(high, low));
                }
                return packUUIDs;
            } catch (IOException e) {
                throw new StoryTellerException("Failed to read pack index on device partition", e);
            }
        });
    }


    public CompletionStage<Boolean> reorderPacks(List<String> uuids) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(noDevicePluggedException());
        }

        return readPackIndex()
                .thenCompose(packUUIDs -> {
                    try {
                        boolean allUUIDsAreOnDevice = uuids.stream()
                                .allMatch(uuid -> packUUIDs.stream().anyMatch(p -> p.equals(UUID.fromString(uuid))));
                        if (allUUIDsAreOnDevice) {
                            // Reorder list according to uuids list
                            packUUIDs.sort(Comparator.comparingInt(p -> uuids.indexOf(p.toString())));
                            // Write pack index
                            return writePackIndex(packUUIDs);
                        } else {
                            throw new StoryTellerException("Packs on device do not match UUIDs");
                        }
                    } catch (Exception e) {
                        throw new StoryTellerException("Failed to read pack metadata on device partition", e);
                    }
                });
    }

    public CompletionStage<Boolean> deletePack(String uuid) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(noDevicePluggedException());
        }

        return readPackIndex()
                .thenCompose(packUUIDs -> {
                    try {
                        // Look for UUID in packs index
                        Optional<UUID> matched = packUUIDs.stream().filter(p -> p.equals(UUID.fromString(uuid))).findFirst();
                        if (matched.isPresent()) {
                            LOGGER.debug("Found pack with uuid: {}", uuid);
                            // Remove from index
                            packUUIDs.remove(matched.get());
                            // Write pack index
                            return writePackIndex(packUUIDs)
                                    .thenCompose(ok -> {
                                        // Generate folder name
                                        String folderName = transformUuid(uuid);
                                        Path folderPath = this.partitionMountPoint.resolve(CONTENT_FOLDER).resolve(folderName);
                                        LOGGER.debug("Removing pack folder: {}", folderPath);
                                        try {
                                            FileUtils.deleteDirectory(folderPath);
                                            return CompletableFuture.completedFuture(ok);
                                        } catch (IOException e) {
                                            return CompletableFuture.failedFuture(new StoryTellerException("Failed to delete pack folder on device partition", e));
                                        }
                                    });
                        } else {
                            throw new StoryTellerException("Pack not found");
                        }
                    } catch (Exception e) {
                        throw new StoryTellerException("Failed to read pack metadata on device partition", e);
                    }
                });
    }

    private CompletionStage<Boolean> writePackIndex(List<UUID> packUUIDs) {
        try {
            Path piFile = this.partitionMountPoint.resolve(PACK_INDEX_FILENAME);

            LOGGER.trace("Replacing pack index file: {}", piFile);
            ByteBuffer bb = ByteBuffer.allocate(16 * packUUIDs.size());
            for (UUID packUUID : packUUIDs) {
                bb.putLong(packUUID.getMostSignificantBits());
                bb.putLong(packUUID.getLeastSignificantBits());
            }
            Files.write(piFile, bb.array());

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new StoryTellerException("Failed to write pack index on device partition", e));
        }
    }

    public CompletionStage<TransferStatus> downloadPack(String uuid, Path destPath, TransferProgressListener listener) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(noDevicePluggedException());
        }
        return readPackIndex()
                .thenCompose(packUUIDs -> CompletableFuture.supplyAsync(() -> {
                    // Look for UUID in packs index
                    Optional<UUID> matched = packUUIDs.stream().filter(p -> p.equals(UUID.fromString(uuid))).findFirst();
                    if (matched.isEmpty()) {
                        throw new StoryTellerException("Pack not found");
                    }
                    LOGGER.debug("Found pack with uuid: {}", uuid);

                    // Generate folder name
                    String folderName = transformUuid(uuid);
                    Path sourceFolder = this.partitionMountPoint.resolve(CONTENT_FOLDER).resolve(folderName);
                    LOGGER.trace("Downloading pack folder: {}", sourceFolder);
                    if (Files.notExists(sourceFolder)) {
                        throw new StoryTellerException("Pack folder not found");
                    }

                    try {
                        // Create destination folder
                        Path destFolder = destPath.resolve(uuid);
                        Files.createDirectories(destFolder);
                        // Copy folder with progress tracking
                        return copyPackFolder(sourceFolder, destFolder, listener);
                    } catch (IOException e) {
                        throw new StoryTellerException("Failed to copy pack from device", e);
                    }
                }));
    }

    public CompletionStage<TransferStatus> uploadPack(String uuid, Path inputPath, TransferProgressListener listener) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(noDevicePluggedException());
        }

        try {
            // Check free space
            long folderSize = FileUtils.getFolderSize(inputPath);
            if(LOGGER.isTraceEnabled()) {
                LOGGER.trace("Pack folder size: {}", FileUtils.readableByteSize(folderSize));
            }
            Path mdFile = this.partitionMountPoint.resolve(DEVICE_METADATA_FILENAME);
            long freeSpace = Files.getFileStore(mdFile).getUsableSpace();
            if(LOGGER.isTraceEnabled()) {
                LOGGER.trace("SD free space: {}", FileUtils.readableByteSize(freeSpace));
            }
            if (freeSpace < folderSize) {
                throw new StoryTellerException("Not enough free space on the device");
            }

            // Generate folder name
            String folderName = transformUuid(uuid);
            Path folderPath = this.partitionMountPoint.resolve(CONTENT_FOLDER).resolve(folderName);
            LOGGER.debug("Uploading pack to folder: {}", folderName);

            // Create destination folder
            Files.createDirectories(folderPath);
            // Copy folder with progress tracking
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return copyPackFolder(inputPath, folderPath, status -> {
                        if (listener != null) {
                            listener.onProgress(status);
                        }
                    });
                } catch (IOException e) {
                    throw new StoryTellerException("Failed to copy pack from device", e);
                }
            }).thenCompose(status -> {
                // When transfer is complete, generate device-specific boot file from device UUID
                LOGGER.debug("Generating device-specific boot file");
                return getDeviceInfos().thenApply(deviceInfos -> {
                    try {
                        FsStoryPackWriter writer = new FsStoryPackWriter();
                        writer.addBootFile(folderPath, deviceInfos.getDeviceId());
                        return status;
                    } catch (IOException e) {
                        throw new StoryTellerException("Failed to generate device-specific boot file", e);
                    }
                });
            }).thenCompose(status -> {
                // Finally, add pack UUID to index
                LOGGER.debug("Add pack uuid to index");
                return readPackIndex().thenCompose(packUUIDs -> {
                    try {
                        // Add UUID in packs index
                        packUUIDs.add(UUID.fromString(uuid));
                        // Write pack index
                        return writePackIndex(packUUIDs).thenApply(ok -> status);
                    } catch (Exception e) {
                        throw new StoryTellerException("Failed to write pack metadata on device partition", e);
                    }
                });
            });
        } catch (IOException e) {
            throw new StoryTellerException("Failed to copy pack to device", e);
        }
    }

    private TransferStatus copyPackFolder(Path sourceFolder, Path destFolder, TransferProgressListener listener) throws IOException {
        // Keep track of transferred bytes and elapsed time
        final long startTime = System.currentTimeMillis();
        AtomicLong transferred = new AtomicLong(0);
        long folderSize = FileUtils.getFolderSize(sourceFolder);
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace("Pack folder size: {}", FileUtils.readableByteSize(folderSize));
        }
        // Copy folders and files
        try(Stream<Path> paths = Files.walk(sourceFolder)) {
            paths.forEach(s -> {
                    try {
                        Path d = destFolder.resolve(sourceFolder.relativize(s));
                        if (Files.isDirectory(s)) {
                            if (!Files.isDirectory(d)) {
                                LOGGER.debug("Creating directory {}", d);
                                Files.createDirectory(d);
                            }
                        } else {
                            long fileSize = FileUtils.getFileSize(s);
                            if(LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Copying file {} ({}) to {}", s.getFileName(), FileUtils.readableByteSize(fileSize), d);
                            }
                            Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);

                            // Compute progress and speed
                            long xferred = transferred.addAndGet(fileSize);
                            long elapsed = System.currentTimeMillis() - startTime;
                            double speed = xferred / (elapsed / 1000.0);
                            if(LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Transferred {} in {} ms", FileUtils.readableByteSize(xferred), elapsed);
                                LOGGER.trace("Average speed = {}/sec", FileUtils.readableByteSize((long)speed));
                            }
                            TransferStatus status = new TransferStatus(xferred == folderSize, xferred, folderSize, speed);

                            // Call (optional) listener with transfer status
                            if (listener != null) {
                                CompletableFuture.runAsync(() -> listener.onProgress(status));
                            }
                        }
                    } catch (IOException e) {
                        throw new StoryTellerException("Failed to copy pack folder", e);
                    }
                });
        }
        return new TransferStatus(transferred.get() == folderSize, transferred.get(), folderSize, 0.0);
    }

    public CompletionStage<Void> dump(Path outputPath) {
        // Not supported
        LOGGER.warn("Not supported : dump");
        return CompletableFuture.completedFuture(null);
    }

    private StoryTellerException noDevicePluggedException() {
        return new StoryTellerException("No device plugged");
    }
}
