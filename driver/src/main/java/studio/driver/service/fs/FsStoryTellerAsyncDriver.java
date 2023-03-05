/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.driver.service.fs;

import static studio.core.v1.utils.io.FileUtils.dataInputStream;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.Device;

import lombok.Setter;
import studio.core.v1.exception.NoDevicePluggedException;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.service.PackFormat;
import studio.core.v1.service.fs.FsStoryPackDTO.FsStoryPack;
import studio.core.v1.service.fs.FsStoryPackDTO.SdPartition;
import studio.core.v1.service.fs.FsStoryPackWriter;
import studio.core.v1.utils.io.FileUtils;
import studio.core.v1.utils.security.SecurityUtils;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.core.v1.model.TransferProgressListener;
import studio.core.v1.model.TransferProgressListener.TransferStatus;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.DeviceInfosDTO.StorageDTO;
import studio.driver.model.MetaPackDTO;
import studio.driver.model.UsbDeviceVersion;
import studio.driver.service.StoryTellerAsyncDriver;
import studio.driver.usb.LibUsbDetectionHelper;

public class FsStoryTellerAsyncDriver implements StoryTellerAsyncDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FsStoryTellerAsyncDriver.class);

    private static final long FS_MOUNTPOINT_POLL_DELAY = 1000L;
    private static final long FS_MOUNTPOINT_RETRY = 10;

    @Setter
    private Device device = null;

    @Setter
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

    List<UUID> readPackIndex() {
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

    void writePackIndex(List<UUID> packUUIDs) {
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
    public CompletionStage<DeviceInfosDTO> getDeviceInfos() {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
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
                    throw new StoryTellerException("Unsupported device metadata format version: " + mdVersion);
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
                byte[] deviceKey = is.readNBytes(256);
                infos.setDeviceKey(deviceKey);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("UUID: {}", SecurityUtils.encodeHex(deviceKey));
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
                throw new StoryTellerException("Fail to read device metadata " + mdFile, e);
            }
            return infos;
        });
    }

    @Override
    public CompletionStage<List<MetaPackDTO>> getPacksList() {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            LOGGER.debug("Number of packs in index: {}", packUUIDs.size());
            List<MetaPackDTO> packs = new ArrayList<>();
            for (UUID uuid : packUUIDs) {
                MetaPackDTO packInfos = new MetaPackDTO();
                packInfos.setFormat(PackFormat.FS.getLabel());
                packInfos.setUuid(uuid);

                // Compute .content folder (last 4 bytes of UUID)
                Path packPath = sdPartition.getPackFolder(uuid);
                packInfos.setFolderName(packPath.getFileName().toString());
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

    @Override
    public CompletionStage<Boolean> reorderPacks(List<UUID> uuids) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            if(!packUUIDs.containsAll(uuids)) {
                throw new StoryTellerException("Packs on device do not match UUIDs");
            }
            // Reorder list according to uuids list
            packUUIDs.sort(Comparator.comparingInt(uuids::indexOf));
            // Write pack index
            writePackIndex(packUUIDs);
            return true;
        });
    }

    @Override
    public CompletionStage<Boolean> deletePack(UUID uuid) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            // Look for UUID in packs index
            if(!packUUIDs.contains(uuid)) {
                throw new StoryTellerException("Pack not found");
            }
            LOGGER.debug("Found pack with uuid: {}", uuid);
            // Remove from index
            packUUIDs.remove(uuid);
            // Write pack index
            writePackIndex(packUUIDs);
            // Get pack folder
            Path packPath = sdPartition.getPackFolder(uuid);
            LOGGER.debug("Removing pack folder: {}", packPath);
            try {
                FileUtils.deleteDirectory(packPath);
                return true;
            } catch (IOException e) {
                throw new StoryTellerException("Failed to delete pack folder on device partition", e);
            }
        });
    }

    @Override
    public CompletionStage<TransferStatus> downloadPack(UUID uuid, Path destFolder, TransferProgressListener listener) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = readPackIndex();
            if(!packUUIDs.contains(uuid)) {
                throw new StoryTellerException("Pack not found");
            }
            LOGGER.debug("Found pack with uuid: {}", uuid);
            // Destination folder
            Path destPath = destFolder.resolve(uuid.toString());
            // Get pack folder
            Path srcPath = sdPartition.getPackFolder(uuid);
            LOGGER.trace("Downloading pack folder: {}", srcPath);
            if (Files.notExists(srcPath)) {
                throw new StoryTellerException("Pack folder not found");
            }
            try {
                // Copy folder with progress tracking
                FileUtils.copyFolder(srcPath, destPath, listener);
                return new TransferStatus(0, 0, 0);
            } catch (IOException e) {
                throw new StoryTellerException("Failed to copy pack from device", e);
            }
        });
    }

    @Override
    public CompletionStage<TransferStatus> uploadPack(UUID uuid, Path srcPath, TransferProgressListener listener) {
        if (!hasDevice() || !hasPartition()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        try {
            // Check free space
            long folderSize = FileUtils.getFolderSize(srcPath);
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

        // Get pack folder
        Path destPath = sdPartition.getPackFolder(uuid);
        LOGGER.debug("Uploading pack to folder: {}", destPath);

        // Copy folder with progress tracking
        return CompletableFuture.supplyAsync(() -> {
            try {
                FileUtils.copyFolder(srcPath, destPath, listener);
                return new TransferStatus(0, 0, 0);
            } catch (IOException e) {
                throw new StoryTellerException("Failed to copy pack from device", e);
            }
        }).thenCompose(status -> {
            // When transfer complete, generate device-specific boot file from device UUID
            LOGGER.debug("Generating device-specific boot file");
            return getDeviceInfos().thenApply(deviceInfos -> {
                FsStoryPackWriter.addBootFile(destPath, deviceInfos.getDeviceKey());
                return status;
            });
        }).thenApplyAsync(status -> {
            // Finally, add pack UUID to index
            LOGGER.debug("Add pack uuid to index");
            List<UUID> packUUIDs = readPackIndex();
            // Add UUID in packs index
            packUUIDs.add(uuid);
            // Write pack index
            writePackIndex(packUUIDs);
            return status;
        });
    }

    @Override
    public CompletionStage<Void> dump(Path outputPath) {
        LOGGER.warn("Not supported : dump");
        return CompletableFuture.completedStage(null);
    }
}

