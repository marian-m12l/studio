/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.driver.service.fs;

import static studio.core.v1.utils.io.FileUtils.dataInputStream;

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

import org.usb4java.Device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.service.PackFormat;
import studio.core.v1.service.fs.FsStoryPackDTO.FsStoryPack;
import studio.core.v1.service.fs.FsStoryPackDTO.SdPartition;
import studio.core.v1.service.fs.FsStoryPackWriter;
import studio.core.v1.utils.io.FileUtils;
import studio.core.v1.utils.security.SecurityUtils;
import studio.core.v1.utils.stream.ThrowingConsumer;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.DeviceInfosDTO.StorageDTO;
import studio.driver.model.MetaPackDTO;
import studio.driver.model.TransferStatus;
import studio.driver.model.UsbDeviceVersion;
import studio.driver.service.StoryTellerAsyncDriver;
import studio.driver.usb.LibUsbDetectionHelper;

public class FsStoryTellerAsyncDriver implements StoryTellerAsyncDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FsStoryTellerAsyncDriver.class);

    private static final long FS_MOUNTPOINT_POLL_DELAY = 1000L;
    private static final long FS_MOUNTPOINT_RETRY = 10;

    private Device device = null;
    private SdPartition sdPartition = null;
    private List<DevicePluggedListener> pluggedlisteners = new ArrayList<>();
    private List<DeviceUnpluggedListener> unpluggedlisteners = new ArrayList<>();

    public FsStoryTellerAsyncDriver() {
        // Initialize libusb, handle and propagate hotplug events
        LOGGER.debug("Registering hotplug listener");
        LibUsbDetectionHelper.initializeLibUsb(UsbDeviceVersion.DEVICE_VERSION_2, //
                device2 -> {
                    // Update device reference
                    sdPartition = findPartition();
                    device = device2;
                    // Notify listeners
                    pluggedlisteners.forEach(l -> l.onDevicePlugged(device2));
                }, //
                device2 -> {
                    // Update device reference
                    sdPartition = null;
                    device = null;
                    // Notify listeners
                    unpluggedlisteners.forEach(l -> l.onDeviceUnplugged(device2));
                });
    }

    @Override
    public boolean hasDevice() {
        return device != null;
    }

    public boolean hasPartition() {
        return sdPartition != null;
    }

    @Override
    public void registerDeviceListener(DevicePluggedListener pluggedlistener,
            DeviceUnpluggedListener unpluggedlistener) {
        this.pluggedlisteners.add(pluggedlistener);
        this.unpluggedlisteners.add(unpluggedlistener);
    }

    /**
     * Wait for a partition to be mounted which contains the .md file
     */
    private SdPartition findPartition() {
        LOGGER.debug("Waiting for device partition...");
        for (int i = 0; i < FS_MOUNTPOINT_RETRY && sdPartition == null; i++) {
            try {
                for(Path path : FileUtils.listMountPoints()) {
                    LOGGER.trace("Looking for .md in {}", path);
                    if (SdPartition.isValid(path)) {
                        LOGGER.info("FS device partition located: {}", path);
                        return new SdPartition(path);
                    }
                }
                Thread.sleep(FS_MOUNTPOINT_POLL_DELAY);
            } catch (InterruptedException e) {
                LOGGER.error("Failed to locate device partition", e);
                Thread.currentThread().interrupt();
            }
        }
        if (!hasPartition()) {
            throw new StoryTellerException("Could not locate device partition");
        }
        return null;
    }

    public CompletionStage<DeviceInfosDTO> getDeviceInfos() {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        DeviceInfosDTO infos = new DeviceInfosDTO();
        infos.setPlugged(true);
        infos.setDriver(PackFormat.FS.getLabel());

        Path mdFile = sdPartition.getDeviceMetada();
        LOGGER.trace("Reading device infos from file: {}", mdFile);

        try (DataInputStream is = dataInputStream(mdFile)) {
            // MD file format version
            short mdVersion = Short.reverseBytes(is.readShort());
            LOGGER.trace("Device metadata format version: {}", mdVersion);
            if (mdVersion < 1 || mdVersion > 3) {
                return CompletableFuture.failedStage(
                        new StoryTellerException("Unsupported device metadata format version: " + mdVersion));
            }

            // Firmware version
            is.skipBytes(4);
            infos.setFirmware(Short.reverseBytes(is.readShort()), Short.reverseBytes(is.readShort()));
            LOGGER.debug("Firmware version: {}", infos.getFirmware());

            // Serial number
            long sn = is.readLong();
            if (sn != 0L && sn != -1L && sn != -4294967296L) {
                infos.setSerial(String.format("%014d", sn));
                LOGGER.debug("Serial Number: {}", infos.getSerial());
            } else {
                LOGGER.warn("No serial number in SPI");
            }

            // UUID
            is.skipBytes(238);
            byte[] deviceId = is.readNBytes(256);
            infos.setUuid(SecurityUtils.encodeHex(deviceId));
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("UUID: {}", infos.getUuid());
            }

            // SD card size and used space
            FileStore mdFd = Files.getFileStore(mdFile);
            long total = mdFd.getTotalSpace();
            long free = mdFd.getUnallocatedSpace();
            long used = total - free;
            infos.setStorage(new StorageDTO(total, free, used));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("SD card used : {} ({} / {})", //
                    FileUtils.readablePercent(used, total), //
                    FileUtils.readableByteSize(used), //
                    FileUtils.readableByteSize(total));
            }
        } catch (IOException e) {
            return CompletableFuture.failedStage(noMetadataPackException(e));
        }
        return CompletableFuture.completedStage(infos);
    }

    @Override
    public CompletionStage<List<MetaPackDTO>> getPacksList() {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            LOGGER.debug("Number of packs in index: {}", packUUIDs.size());
            List<MetaPackDTO> packs = new ArrayList<>();
            for (UUID packUUID : packUUIDs) {
                String uuid = packUUID.toString();
                MetaPackDTO packInfos = new MetaPackDTO();
                packInfos.setFormat(PackFormat.FS.getLabel());
                packInfos.setUuid(uuid);

                // Compute .content folder (last 4 bytes of UUID)
                String folderName = transformUuid(uuid);
                packInfos.setFolderName(folderName);
                Path packPath = sdPartition.getContentFolder().resolve(folderName);
                FsStoryPack fsp = new FsStoryPack(packPath);
                // Night mode is available if file 'nm' exists
                packInfos.setNightModeAvailable(fsp.isNightModeAvailable());

                // Open 'ni' file
                try (DataInputStream niDis = dataInputStream(fsp.getNodeIndex())) {
                    niDis.readShort();
                    packInfos.setVersion(Short.reverseBytes(niDis.readShort()));
                    // Compute folder size
                    packInfos.setSizeInBytes(FileUtils.getFolderSize(packPath));
                } catch (IOException e) {
                    throw new StoryTellerException("Failed to read pack version " + packPath, e);
                }
                packs.add(packInfos);
                LOGGER.debug("Pack v{} {} ({})", packInfos.getVersion(), packInfos.getFolderName(),
                        packInfos.getUuid());
            }
            return packs;
        });
    }

    private List<UUID> readPackIndex() {
        List<UUID> packUUIDs = new ArrayList<>();
        Path piFile = sdPartition.getPackIndex();
        LOGGER.trace("Reading packs index from file: {}", piFile);
        try {
            ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(piFile));
            while (bb.hasRemaining()) {
                packUUIDs.add(new UUID(bb.getLong(), bb.getLong()));
            }
            return packUUIDs;
        } catch (IOException e) {
            throw new StoryTellerException("Failed to read pack index on device partition", e);
        }
    }

    @Override
    public CompletionStage<Boolean> reorderPacks(List<String> uuids) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            boolean allUUIDsAreOnDevice = uuids.stream()
                    .allMatch(uuid -> packUUIDs.stream().anyMatch(p -> p.equals(UUID.fromString(uuid))));
            if (!allUUIDsAreOnDevice) {
                throw new StoryTellerException("Packs on device do not match UUIDs");
            }
            // Reorder list according to uuids list
            packUUIDs.sort(Comparator.comparingInt(p -> uuids.indexOf(p.toString())));
            // Write pack index
            writePackIndex(packUUIDs);
            return true;
        });
    }

    @Override
    public CompletionStage<Boolean> deletePack(String uuid) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            // Look for UUID in packs index
            Optional<UUID> matched = packUUIDs.stream().filter(p -> p.equals(UUID.fromString(uuid))).findFirst();
            if (matched.isEmpty()) {
                throw new StoryTellerException("Pack not found");
            }
            LOGGER.debug("Found pack with uuid: {}", uuid);
            // Remove from index
            packUUIDs.remove(matched.get());
            // Write pack index
            writePackIndex(packUUIDs);
            // Generate folder name
            String folderName = transformUuid(uuid);
            Path folderPath = sdPartition.getContentFolder().resolve(folderName);
            LOGGER.debug("Removing pack folder: {}", folderPath);
            try {
                FileUtils.deleteDirectory(folderPath);
                return true;
            } catch (IOException e) {
                throw new StoryTellerException("Failed to delete pack folder on device partition", e);
            }
        });
    }

    private void writePackIndex(List<UUID> packUUIDs) {
        Path piFile = sdPartition.getPackIndex();
        LOGGER.info("Replacing pack index file: {}", piFile);
        ByteBuffer bb = ByteBuffer.allocate(16 * packUUIDs.size());
        for (UUID packUUID : packUUIDs) {
            bb.putLong(packUUID.getMostSignificantBits());
            bb.putLong(packUUID.getLeastSignificantBits());
        }
        try {
            Files.write(piFile, bb.array());
        } catch (IOException e) {
            throw new StoryTellerException("Failed to write pack index on device partition", e);
        }
    }

    @Override
    public CompletionStage<TransferStatus> downloadPack(String uuid, Path destPath, TransferProgressListener listener) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            // Look for UUID in packs index
            Optional<UUID> matched = packUUIDs.stream().filter(p -> p.equals(UUID.fromString(uuid))).findFirst();
            if (matched.isEmpty()) {
                throw new StoryTellerException("Pack not found");
            }
            LOGGER.debug("Found pack with uuid: {}", uuid);
            // Destination folder
            Path destFolder = destPath.resolve(uuid);
            // Generate folder name
            String folderName = transformUuid(uuid);
            Path sourceFolder = sdPartition.getContentFolder().resolve(folderName);
            LOGGER.trace("Downloading pack folder: {}", sourceFolder);
            if (Files.notExists(sourceFolder)) {
                throw new StoryTellerException("Pack folder not found");
            }
            try {
                // Copy folder with progress tracking
                return copyPackFolder(sourceFolder, destFolder, listener);
            } catch (IOException e) {
                throw new StoryTellerException("Failed to copy pack from device", e);
            }
        });
    }

    @Override
    public CompletionStage<TransferStatus> uploadPack(String uuid, Path inputPath, TransferProgressListener listener) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        try {
            // Check free space
            long folderSize = FileUtils.getFolderSize(inputPath);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Pack folder size: {}", FileUtils.readableByteSize(folderSize));
            }
            Path mdFile = sdPartition.getDeviceMetada();
            long freeSpace = Files.getFileStore(mdFile).getUsableSpace();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("SD free space: {}", FileUtils.readableByteSize(freeSpace));
            }
            if (freeSpace < folderSize) {
                throw new StoryTellerException("Not enough free space on the device");
            }
        } catch (IOException e) {
            throw new StoryTellerException("Failed to check device space", e);
        }

        // Generate folder name
        String folderName = transformUuid(uuid);
        Path folderPath = sdPartition.getContentFolder().resolve(folderName);
        LOGGER.debug("Uploading pack to folder: {}", folderName);

        // Copy folder with progress tracking
        return CompletableFuture.supplyAsync(() -> {
            try {
                return copyPackFolder(inputPath, folderPath, listener);
            } catch (IOException e) {
                throw new StoryTellerException("Failed to copy pack from device", e);
            }
        }).thenCompose(status -> {
            // When transfer complete, generate device-specific boot file from device UUID
            LOGGER.debug("Generating device-specific boot file");
            return getDeviceInfos().thenApply(deviceInfos -> {
                try {
                    byte [] deviceId = SecurityUtils.decodeHex(deviceInfos.getUuid());
                    FsStoryPackWriter.addBootFile(folderPath, deviceId);
                } catch (IOException e) {
                    throw new StoryTellerException("Failed to create boot file", e);
                }
                return status;
            });
        }).thenApplyAsync(status -> {
            // Finally, add pack UUID to index
            LOGGER.debug("Add pack uuid to index");
            List<UUID> packUUIDs = readPackIndex();
            // Add UUID in packs index
            packUUIDs.add(UUID.fromString(uuid));
            // Write pack index
            writePackIndex(packUUIDs);
            return status;
        });
    }

    private static TransferStatus copyPackFolder(Path sourceFolder, Path destFolder, TransferProgressListener listener)
            throws IOException {
        // Keep track of transferred bytes and elapsed time
        final long startTime = System.currentTimeMillis();
        AtomicLong transferred = new AtomicLong(0);
        long folderSize = FileUtils.getFolderSize(sourceFolder);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Pack folder size: {}", FileUtils.readableByteSize(folderSize));
        }
        // Target directory
        Files.createDirectories(destFolder);
        // Copy folders and files
        try (Stream<Path> paths = Files.walk(sourceFolder)) {
            paths.forEach(ThrowingConsumer.unchecked(s -> {
                Path d = destFolder.resolve(sourceFolder.relativize(s));
                // Copy directory
                if (Files.isDirectory(s)) {
                    LOGGER.debug("Creating directory {}", d);
                    Files.createDirectories(d);
                } else {
                    // Copy files
                    long fileSize = FileUtils.getFileSize(s);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Copying file {} ({}) to {}", s.getFileName(),
                                FileUtils.readableByteSize(fileSize), d);
                    }
                    Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);

                    // Compute progress and speed
                    long xferred = transferred.addAndGet(fileSize);
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speed = xferred / (elapsed / 1000.0);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Transferred {} in {} ms", FileUtils.readableByteSize(xferred), elapsed);
                        LOGGER.trace("Average speed = {}/sec", FileUtils.readableByteSize((long) speed));
                    }
                    TransferStatus status = new TransferStatus(xferred, folderSize, speed);

                    // Call (optional) listener with transfer status
                    if (listener != null) {
                        CompletableFuture.runAsync(() -> listener.onProgress(status));
                    }
                }
            }));
        }
        return new TransferStatus(transferred.get(), folderSize, 0.0);
    }

    @Override
    public CompletionStage<Void> dump(Path outputPath) {
        LOGGER.warn("Not supported : dump");
        return CompletableFuture.completedStage(null);
    }

    public static String transformUuid(String uuid) {
        String uuidStr = uuid.replace("-", "");
        return uuidStr.substring(uuidStr.length() - 8).toUpperCase();
    }

    private static StoryTellerException noDevicePluggedException() {
        return new StoryTellerException("No device plugged");
    }

    private static StoryTellerException noMetadataPackException(Exception e) {
        return new StoryTellerException("Failed to read pack metadata on device partition", e);
    }
}
