/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.fs;

import org.apache.commons.codec.binary.Hex;
import org.usb4java.Device;
import studio.driver.DeviceVersion;
import studio.driver.LibUsbDetectionHelper;
import studio.driver.model.fs.FsDeviceInfos;
import studio.driver.model.fs.FsDeviceKeyV3;
import studio.driver.model.fs.FsStoryPackInfos;
import studio.driver.StoryTellerException;
import studio.driver.event.DeviceHotplugEventListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.TransferStatus;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FsStoryTellerAsyncDriver {

    private static final Logger LOGGER = Logger.getLogger(FsStoryTellerAsyncDriver.class.getName());

    private static final String DEVICE_METADATA_FILENAME = ".md";
    private static final String PACK_INDEX_FILENAME = ".pi";
    private static final String CONTENT_FOLDER = ".content";
    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String NIGHT_MODE_FILENAME = "nm";

    private static final long FS_MOUNTPOINT_POLL_DELAY = 1000L;
    private static final long FS_MOUNTPOINT_RETRY = 10;


    private Device device = null;
    private String partitionMountPoint = null;
    private List<DeviceHotplugEventListener> listeners = new ArrayList<>();


    public FsStoryTellerAsyncDriver() {
        // Initialize libusb, handle and propagate hotplug events
        LOGGER.fine("Registering hotplug listener");
        LibUsbDetectionHelper.initializeLibUsb(DeviceVersion.DEVICE_VERSION_2, new DeviceHotplugEventListener() {
                    @Override
                    public void onDevicePlugged(Device device) {
                        // Wait for a partition to be mounted which contains the .md file
                        LOGGER.fine("Waiting for device partition...");
                        for (int i = 0; i < FS_MOUNTPOINT_RETRY && partitionMountPoint==null; i++) {
                            try {
                                Thread.sleep(FS_MOUNTPOINT_POLL_DELAY);
                                DeviceUtils.listMountPoints().forEach(path -> {
                                    LOGGER.finest("Looking for .md file on mount point / drive: " + path);
                                    File mdFile = new File(path, DEVICE_METADATA_FILENAME);
                                    if (mdFile.exists()) {
                                        partitionMountPoint = path;
                                        LOGGER.info("FS device partition located: " + partitionMountPoint);
                                    }
                                });
                            } catch (InterruptedException e) {
                                LOGGER.log(Level.SEVERE, "Failed to locate device partition", e);
                            }
                        }

                        if (partitionMountPoint == null) {
                            throw new StoryTellerException("Could not locate device partition");
                        }

                        // Update device reference
                        FsStoryTellerAsyncDriver.this.device = device;
                        // Notify listeners
                        FsStoryTellerAsyncDriver.this.listeners.forEach(listener -> listener.onDevicePlugged(device));
                    }

                    @Override
                    public void onDeviceUnplugged(Device device) {
                        // Update device reference
                        FsStoryTellerAsyncDriver.this.device = null;
                        FsStoryTellerAsyncDriver.this.partitionMountPoint = null;
                        // Notify listeners
                        FsStoryTellerAsyncDriver.this.listeners.forEach(listener -> listener.onDeviceUnplugged(device));
                    }
                }
        );
    }


    public void registerDeviceListener(DeviceHotplugEventListener listener) {
        this.listeners.add(listener);
        if (this.device != null) {
            listener.onDevicePlugged(this.device);
        }
    }


    public CompletableFuture<FsDeviceInfos> getDeviceInfos() {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(new StoryTellerException("No device plugged"));
        }
        FsDeviceInfos infos = new FsDeviceInfos();
        try {
            String mdFile = this.partitionMountPoint + File.separator + DEVICE_METADATA_FILENAME;
            LOGGER.finest("Reading device infos from file: " + mdFile);
            FileInputStream deviceMetadataFis = new FileInputStream(mdFile);

            // MD file format version
            short mdVersion = readLittleEndianShort(deviceMetadataFis);
            LOGGER.finest("Device metadata format version: " + mdVersion);
            if (mdVersion >= 1 && mdVersion <= 3) {
                this.parseDeviceInfosMeta1to3(infos, deviceMetadataFis);
            } else if (mdVersion == 6) {
                this.parseDeviceInfosMeta6(infos, deviceMetadataFis);
            } else {
                return CompletableFuture.failedFuture(new StoryTellerException("Unsupported device metadata format version: " + mdVersion));
            }

            deviceMetadataFis.close();

            // SD card size and used space
            File mdFd = new File(mdFile);
            long sdCardTotalSpace = mdFd.getTotalSpace();
            long sdCardUsedSpace = mdFd.getTotalSpace() - mdFd.getFreeSpace();
            infos.setSdCardSizeInBytes(sdCardTotalSpace);
            infos.setUsedSpaceInBytes(sdCardUsedSpace);
            LOGGER.fine("SD card size: " + sdCardTotalSpace);
            LOGGER.fine("SD card used space: " + sdCardUsedSpace);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new StoryTellerException("Failed to read device metadata on partition", e));
        }

        return CompletableFuture.completedFuture(infos);
    }

    private void parseDeviceInfosMeta1to3(FsDeviceInfos infos, FileInputStream deviceMetadataFis) throws IOException {
        // Firmware version
        deviceMetadataFis.skip(4);
        short major = readLittleEndianShort(deviceMetadataFis);
        short minor = readLittleEndianShort(deviceMetadataFis);
        infos.setFirmwareMajor(major);
        infos.setFirmwareMinor(minor);
        LOGGER.fine("Firmware version: " + major + "." + minor);

        // Serial number
        String serialNumber = null;
        long sn = readBigEndianLong(deviceMetadataFis);
        if (sn != 0L && sn != -1L && sn != -4294967296L) {
            serialNumber = String.format("%014d", sn);
            LOGGER.fine("Serial Number: " + serialNumber);
        } else {
            LOGGER.warning("No serial number in SPI");
        }
        infos.setSerialNumber(serialNumber);

        // UUID
        deviceMetadataFis.skip(238);
        byte[] uuid = deviceMetadataFis.readNBytes(256);
        infos.setUuid(uuid);
        LOGGER.fine("UUID: " + Hex.encodeHexString(uuid));
    }

    private void parseDeviceInfosMeta6(FsDeviceInfos infos, FileInputStream deviceMetadataFis) throws IOException {
        // Firmware version
        short major = readAsciiToShort(deviceMetadataFis, 1);
        deviceMetadataFis.skip(1);
        short minor = readAsciiToShort(deviceMetadataFis, 1);
        infos.setFirmwareMajor(major);
        infos.setFirmwareMinor(minor);
        LOGGER.fine("Firmware version: " + major + "." + minor);

        // Serial number
        deviceMetadataFis.skip(21);
        byte[] snBytes = deviceMetadataFis.readNBytes(24);
        String serialNumber = new String(snBytes);
        LOGGER.info("Serial Number: " + serialNumber);
        infos.setSerialNumber(serialNumber);

        // Construct AES key and IV from serial number
        byte[] aesKey = new byte[16];
        System.arraycopy(snBytes, 0, aesKey, 0 , 16);
        byte[] aesIv = new byte[16];
        System.arraycopy(snBytes, 16, aesIv, 0 , 8);
        System.arraycopy(snBytes, 0, aesIv, 8 , 8);

        // BT file content
        deviceMetadataFis.skip(14);
        byte[] btFile = deviceMetadataFis.readNBytes(32);

        infos.setDeviceKeyV3(new FsDeviceKeyV3(aesKey, aesIv, btFile));

        // Dummy UUID
        byte[] uuid = new byte[64];
        System.arraycopy(aesKey, 0, uuid, 0 , 16);
        System.arraycopy(aesIv, 0, uuid, 16 , 16);
        System.arraycopy(btFile, 0, uuid, 32 , 32);
        infos.setUuid(uuid);
        LOGGER.fine("UUID: " + Hex.encodeHexString(uuid));
    }

    private short readLittleEndianShort(FileInputStream fis) throws IOException {
        byte[] buffer = new byte[2];
        fis.read(buffer);
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getShort();
    }

    private long readBigEndianLong(FileInputStream fis) throws IOException {
        byte[] buffer = new byte[8];
        fis.read(buffer);
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getLong();
    }

    private short readAsciiToShort(FileInputStream fis, int numberBytes) throws IOException {
        return Short.parseShort(new String(fis.readNBytes(numberBytes), StandardCharsets.UTF_8));
    }

    private long readAsciiToLong(FileInputStream fis, int numberBytes) throws IOException {
        return Long.parseLong(new String(fis.readNBytes(numberBytes), StandardCharsets.UTF_8));
    }


    public CompletableFuture<List<FsStoryPackInfos>> getPacksList() {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(new StoryTellerException("No device plugged"));
        }

        return readPackIndex()
                .thenApply(packUUIDs -> {
                    try {
                        LOGGER.fine("Number of packs in index: " + packUUIDs.size());
                        List<FsStoryPackInfos> packs = new ArrayList<>();
                        for (UUID packUUID : packUUIDs) {
                            FsStoryPackInfos packInfos = new FsStoryPackInfos();
                            packInfos.setUuid(packUUID);
                            LOGGER.fine("Pack UUID: " + packUUID.toString());

                            // Compute .content folder (last 4 bytes of UUID)
                            String folderName = computePackFolderName(packUUID.toString());
                            String packFolderPath = this.partitionMountPoint + File.separator + CONTENT_FOLDER + File.separator + folderName;
                            packInfos.setFolderName(folderName);

                            // Open 'ni' file
                            File packFolder = new File(packFolderPath);
                            FileInputStream niFis = new FileInputStream(new File(packFolder, NODE_INDEX_FILENAME));
                            DataInputStream niDis = new DataInputStream(niFis);
                            ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
                            short version = bb.getShort(2);
                            packInfos.setVersion(version);
                            LOGGER.fine("Pack version: " + version);
                            niDis.close();
                            niFis.close();

                            // Night mode is available if file 'nm' exists
                            packInfos.setNightModeAvailable(new File(packFolder, NIGHT_MODE_FILENAME).exists());

                            // Compute folder size
                            packInfos.setSizeInBytes((int) FileUtils.getFolderSize(packFolderPath));

                            packs.add(packInfos);
                        }
                        return packs;
                    } catch (Exception e) {
                        throw new StoryTellerException("Failed to read pack metadata on device partition", e);
                    }
                });
    }

    private CompletableFuture<List<UUID>> readPackIndex() {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> packUUIDs = new ArrayList<>();
            try {
                String piFile = this.partitionMountPoint + File.separator + PACK_INDEX_FILENAME;

                LOGGER.finest("Reading packs index from file: " + piFile);
                FileInputStream packIndexFis = new FileInputStream(piFile);

                byte[] packUuid = new byte[16];
                while (packIndexFis.read(packUuid) > 0) {
                    ByteBuffer bb = ByteBuffer.wrap(packUuid);
                    long high = bb.getLong();
                    long low = bb.getLong();
                    packUUIDs.add(new UUID(high, low));
                }

                packIndexFis.close();

                return packUUIDs;
            } catch (Exception e) {
                throw new StoryTellerException("Failed to read pack index on device partition", e);
            }
        });
    }


    public CompletableFuture<Boolean> reorderPacks(List<String> uuids) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(new StoryTellerException("No device plugged"));
        }

        return readPackIndex()
                .thenCompose(packUUIDs -> {
                    try {
                        boolean allUUIDsAreOnDevice = uuids.stream().allMatch(uuid -> packUUIDs.stream().anyMatch(p -> p.equals(UUID.fromString(uuid))));
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

    public CompletableFuture<Boolean> deletePack(String uuid) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(new StoryTellerException("No device plugged"));
        }

        return readPackIndex()
                .thenCompose(packUUIDs -> {
                    try {
                        // Look for UUID in packs index
                        Optional<UUID> matched = packUUIDs.stream().filter(p -> p.equals(UUID.fromString(uuid))).findFirst();
                        if (matched.isPresent()) {
                            LOGGER.fine("Found pack with uuid: " + uuid);
                            // Remove from index
                            packUUIDs.remove(matched.get());
                            // Write pack index
                            return writePackIndex(packUUIDs)
                                    .thenCompose(ok -> {
                                        // Generate folder name
                                        String folderName = this.partitionMountPoint + File.separator + CONTENT_FOLDER + File.separator + computePackFolderName(uuid);
                                        LOGGER.fine("Removing pack folder: " + folderName);
                                        try {
                                            org.apache.commons.io.FileUtils.deleteDirectory(new File(folderName));
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

    private CompletableFuture<Boolean> writePackIndex(List<UUID> packUUIDs) {
        // Because the hidden file cannot be modified on windows, we need to write to a temporary file
        try {
            String piFile = this.partitionMountPoint + File.separator + PACK_INDEX_FILENAME;
            String newPiFile = piFile + ".new";
            LOGGER.finest("Writing pack index to temporary file: " + newPiFile);

            try(
                    FileOutputStream packIndexFos = new FileOutputStream(newPiFile);
                    DataOutputStream packIndexDos = new DataOutputStream(packIndexFos);
            ) {
                for (UUID packUUID : packUUIDs) {
                    packIndexDos.writeLong(packUUID.getMostSignificantBits());
                    packIndexDos.writeLong(packUUID.getLeastSignificantBits());
                }
            }

            // Then replace file
            LOGGER.finest("Replacing pack index file");
            Files.copy(Paths.get(newPiFile), Paths.get(this.partitionMountPoint + File.separator + PACK_INDEX_FILENAME), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.finest("Deleting temporary pack index file");
            Files.delete(Paths.get(newPiFile));

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new StoryTellerException("Failed to write pack index on device partition", e));
        }
    }


    public CompletableFuture<TransferStatus> downloadPack(String uuid, String outputPath, TransferProgressListener listener) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(new StoryTellerException("No device plugged"));
        }

        return readPackIndex()
                .thenCompose(packUUIDs -> getDeviceInfos()
                        .thenCompose(deviceInfos -> CompletableFuture.supplyAsync(() -> {
                    // Look for UUID in packs index
                    Optional<UUID> matched = packUUIDs.stream().filter(p -> p.equals(UUID.fromString(uuid))).findFirst();
                    if (matched.isPresent()) {
                        LOGGER.fine("Found pack with uuid: " + uuid);

                        // Generate folder name
                        String sourceFolder = this.partitionMountPoint + File.separator + CONTENT_FOLDER + File.separator + computePackFolderName(uuid);
                        LOGGER.finest("Downloading pack folder: " + sourceFolder);

                        if (Files.exists(Paths.get(sourceFolder))) {
                            try {
                                // Create destination folder
                                File destFolder = new File(outputPath + File.separator + uuid);
                                destFolder.mkdirs();
                                // Copy folder with progress tracking
                                return copyPackFolder(sourceFolder, destFolder, deviceInfos, false, listener);
                            } catch (IOException e) {
                                throw new StoryTellerException("Failed to copy pack from device", e);
                            }
                        } else {
                            throw new StoryTellerException("Pack folder not found");
                        }
                    } else {
                        throw new StoryTellerException("Pack not found");
                    }
                })));
    }

    public CompletableFuture<TransferStatus> uploadPack(String uuid, String inputPath, TransferProgressListener listener) {
        if (this.device == null || this.partitionMountPoint == null) {
            return CompletableFuture.failedFuture(new StoryTellerException("No device plugged"));
        }

        try {
            // Check free space
            int folderSize = (int) FileUtils.getFolderSize(inputPath);
            LOGGER.finest("Pack folder size: " + folderSize);
            String mdFile = this.partitionMountPoint + File.separator + DEVICE_METADATA_FILENAME;
            File mdFd = new File(mdFile);
            if (mdFd.getFreeSpace() < folderSize) {
                throw new StoryTellerException("Not enough free space on the device");
            }

            // Generate folder name
            String folderName = this.partitionMountPoint + File.separator + CONTENT_FOLDER + File.separator + computePackFolderName(uuid);
            LOGGER.fine("Uploading pack to folder: " + folderName);

            // Create destination folder
            File destFolder = new File(folderName);
            destFolder.mkdirs();
            // Copy folder with progress tracking
            return getDeviceInfos().thenCompose(deviceInfos ->
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return copyPackFolder(inputPath, destFolder, deviceInfos, true, new TransferProgressListener() {
                                @Override
                                public void onProgress(TransferStatus status) {
                                    if (listener != null) {
                                        listener.onProgress(status);
                                    }
                                }

                                @Override
                                public void onComplete(TransferStatus status) {
                                    // Not calling listener because the pack must be added to the index
                                }
                            });
                        } catch (IOException e) {
                            throw new StoryTellerException("Failed to copy pack from device", e);
                        }
                    })).thenCompose(status -> {
                        // Finally, add pack UUID to index
                        return readPackIndex()
                                .thenCompose(packUUIDs -> {
                                    try {
                                        // Add UUID in packs index
                                        packUUIDs.add(UUID.fromString(uuid));
                                        // Write pack index
                                        return writePackIndex(packUUIDs)
                                                .thenApply(ok -> {
                                                    if (listener != null) {
                                                        listener.onComplete(status);
                                                    }
                                                    return status;
                                                });
                                    } catch (Exception e) {
                                        throw new StoryTellerException("Failed to write pack metadata on device partition", e);
                                    }
                                });
                    });
        } catch (IOException e) {
            throw new StoryTellerException("Failed to copy pack to device", e);
        }
    }

    private TransferStatus copyPackFolder(String sourceFolder, File destFolder, FsDeviceInfos deviceInfos, boolean isUpload, TransferProgressListener listener) throws IOException {
        // Keep track of transferred bytes and elapsed time
        final long startTime = System.currentTimeMillis();
        AtomicInteger transferred = new AtomicInteger(0);
        int folderSize = (int) FileUtils.getFolderSize(sourceFolder);
        LOGGER.finest("Pack folder size: " + folderSize);

        // Fail for unsupported firmware versions
        if (deviceInfos.getFirmwareMajor() != 2 && deviceInfos.getFirmwareMajor() != 3) {
            throw new StoryTellerException("Failed to copy pack folder: unsupported firmware version " + deviceInfos.getFirmwareMajor());
        }

        // Assets are cleartext if file '.cleartext' exists
        boolean isCleartext = new File(sourceFolder, CipherUtils.CLEARTEXT_FILENAME).exists();

        // Copy folders and files
        Files.walk(Paths.get(sourceFolder))
                .forEach(s -> {
                    try {
                        Path d = destFolder.toPath().resolve(Paths.get(sourceFolder).relativize(s));
                        if (Files.isDirectory(s)) {
                            if (!Files.exists(d)) {
                                LOGGER.finer("Creating directory " + d.toString());
                                Files.createDirectory(d);
                            }
                        } else {
                            // DO NOT COPY .cleartext file
                            if (!CipherUtils.shouldBeCopied(s)) {
                                LOGGER.finer("NOT copying file " + s.toString());
                                return;
                            }

                            int fileSize = (int) FileUtils.getFileSize(s.toAbsolutePath().toString());
                            LOGGER.finer("Copying file " + s.toString() + " to " + d.toString() + " (" + fileSize + " bytes)");

                            if (CipherUtils.shouldBeCiphered(s)) {
                                if (deviceInfos.getFirmwareMajor() == 2) {
                                    if (isUpload) {
                                        if (isCleartext) {
                                            byte[] ciphered = CipherUtils.cipherFirstBlockCommonKey(Files.readAllBytes(s));
                                            Files.write(d, ciphered);
                                        } else {
                                            Files.copy(s, d);
                                        }
                                    } else {    // Download
                                        byte[] deciphered = CipherUtils.decipherFirstBlockCommonKey(Files.readAllBytes(s));
                                        Files.write(d, deciphered);
                                    }
                                } else {    // V3
                                    if (isUpload) {
                                        byte[] data = Files.readAllBytes(s);
                                        if (!isCleartext) {
                                            data = CipherUtils.decipherFirstBlockCommonKey(data);
                                        }
                                        byte[] ciphered = CipherUtils.cipherFirstBlockSpecificKeyV3(data, deviceInfos.getDeviceKeyV3());
                                        Files.write(d, ciphered);
                                    } else {    // Download
                                        byte[] deciphered = CipherUtils.decipherFirstBlockSpecificKeyV3(Files.readAllBytes(s), deviceInfos.getDeviceKeyV3());
                                        Files.write(d, deciphered);
                                    }
                                }
                            } else {
                                Files.copy(s, d);
                            }

                            // Compute progress and speed
                            int xferred = transferred.addAndGet(fileSize);
                            long elapsed = System.currentTimeMillis() - startTime;
                            double speed = ((double) xferred) / ((double) elapsed / 1000.0);
                            LOGGER.finer("Transferred " + xferred + " bytes in " + elapsed + " ms");
                            LOGGER.finer("Average speed = " + speed + " bytes/sec");
                            TransferStatus status = new TransferStatus(xferred == folderSize, xferred, folderSize, speed);

                            // Call (optional) listener with transfer status
                            if (listener != null) {
                                CompletableFuture.runAsync(() -> listener.onProgress(status));
                                if (status.isDone()) {
                                    CompletableFuture.runAsync(() -> listener.onComplete(status));
                                }
                            }
                        }
                    } catch (Exception e) {
                        throw new StoryTellerException("Failed to copy pack folder", e);
                    }
                });
        // When transfer is complete, generate device-specific boot file
        LOGGER.fine("Generating device-specific boot file");
        try {
            if (deviceInfos.getFirmwareMajor() == 2) {
                CipherUtils.addBootFileV2(destFolder.toPath(), deviceInfos.getUuid());
            } else {
                CipherUtils.addBootFileV3(destFolder.toPath(), deviceInfos.getDeviceKeyV3());
            }
        } catch (IOException e) {
            throw new StoryTellerException("Failed to generate device-specific boot file", e);
        }
        return new TransferStatus(transferred.get() == folderSize, transferred.get(), folderSize, 0.0);
    }

    public String computePackFolderName(String uuid) {
        String uuidStr = uuid.replaceAll("-", "");
        return uuidStr.substring(uuidStr.length() - 8).toUpperCase();
    }
}
