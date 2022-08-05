/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.service.raw;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.usb4java.Device;
import org.usb4java.DeviceHandle;

import lombok.Getter;
import lombok.Setter;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.service.PackFormat;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.model.DeviceInfos;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.DeviceInfosDTO.StorageDTO;
import studio.driver.model.DeviceVersion;
import studio.driver.model.MetaPackDTO;
import studio.driver.model.TransferStatus;
import studio.driver.service.StoryTellerAsyncDriver;
import studio.driver.usb.LibUsbDetectionHelper;

public class RawStoryTellerAsyncDriver implements StoryTellerAsyncDriver {

    private static final Logger LOGGER = LogManager.getLogger(RawStoryTellerAsyncDriver.class);

    private static final int SDCARD_DEFAULT_SIZE_IN_SECTORS = 6815513;
    private static final int SDCARD_FAT16_PARTITION_SIZE_IN_SECTORS = 20480;    // 10.5 MB
    private static final int DEVICE_INFOS_SPI_OFFSET = 520192;
    private static final int DEVICE_INFOS_SD_SECTOR_0 = 0;
    private static final int DEVICE_INFOS_SD_SECTOR_2 = 2;
    private static final int PACK_INDEX_SD_SECTOR = 100000;
    private static final int PACK_TRANSFER_CHUNK_SIZE_IN_SECTORS = 5000;    // 2.5 MB

    private Device device = null;
    private List<DevicePluggedListener> pluggedlisteners = new ArrayList<>();
    private List<DeviceUnpluggedListener> unpluggedlisteners = new ArrayList<>();

    @Getter
    @Setter
    private static class RawDeviceInfos extends DeviceInfos {
        private UUID uuid;
        private int sdCardSizeInSectors;
        private int usedSpaceInSectors;
        private boolean inError;
    }

    @Getter
    @Setter
    private static class RawStoryPackInfos {
        private UUID uuid;
        private short version;
        private int startSector;
        private int sizeInSectors;
        private short statsOffset;
        private short samplingRate;
    }

    public RawStoryTellerAsyncDriver() {
        // Initialize libusb, handle and propagate hotplug events
        LOGGER.debug("Registering hotplug listener");
        LibUsbDetectionHelper.initializeLibUsb(DeviceVersion.DEVICE_VERSION_1, device2 -> {
            // Update device reference
            this.device = device2;
            // Notify listeners
            this.pluggedlisteners.forEach(l -> l.onDevicePlugged(device2));
        }, device2 -> {
            // Update device reference
            this.device = null;
            // Notify listeners
            this.unpluggedlisteners.forEach(l -> l.onDeviceUnplugged(device2));
        });
    }

    @Override
    public boolean hasDevice() {
        return device != null;
    }

    @Override
    public void registerDeviceListener(DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener) {
        this.pluggedlisteners.add(pluggedlistener);
        this.unpluggedlisteners.add(unpluggedlistener);
    }

    @Override
    public CompletionStage<DeviceInfosDTO> getDeviceInfos() {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(device, this::readDeviceInfos)//
                .thenApply(infos -> {
                    long total = (long) infos.getSdCardSizeInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE;
                    long used = (long) infos.getUsedSpaceInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE;
                    String fw = infos.getFirmwareMajor() == -1 ? null : infos.getFirmwareMajor() + "." + infos.getFirmwareMinor();

                    DeviceInfosDTO di = new DeviceInfosDTO();
                    di.setUuid(infos.getUuid().toString());
                    di.setSerial(infos.getSerialNumber());
                    di.setFirmware(fw);
                    di.setError(infos.isInError());
                    di.setPlugged(true);
                    di.setDriver(PackFormat.RAW.getLabel());
                    di.setStorage(new StorageDTO(total, total - used, used));
                    return di;
                });
    }

    private CompletionStage<RawDeviceInfos> readDeviceInfos(DeviceHandle handle) {
        // Read UUID and Serial Number from SPI
        return LibUsbMassStorageHelper.asyncReadSPISectors(handle, DEVICE_INFOS_SPI_OFFSET, (short) 1)
                // Read firmware version, card size and error from SD
                .thenCombine(LibUsbMassStorageHelper.asyncReadSDSectors(handle, DEVICE_INFOS_SD_SECTOR_2, (short) 1),
                        this::readRawDeviceInfos)
                // Compute used SD card space from packs index
                .thenCompose(rd -> readPackIndex(handle).thenApply(packs -> {
                    int usedSpace = packs.stream().map(RawStoryPackInfos::getSizeInSectors).reduce(0, Integer::sum);
                    rd.setUsedSpaceInSectors(usedSpace);
                    return rd;
                }));
    }

    public static boolean checkUuidBytes(long lowBytes, long highBytes) {
        return Stream.of(0L, -1L, -4294967296L).allMatch(v -> lowBytes != v || highBytes != v);
    }

    private RawDeviceInfos readRawDeviceInfos(ByteBuffer spiSector, ByteBuffer sdSector) {
        RawDeviceInfos rd = new RawDeviceInfos();

        UUID uuid = null;
        long uuidLowBytes = spiSector.getLong(8); // Read low 8 bytes
        long uuidHighBytes = spiSector.getLong(16); // Read high 8 bytes
        if (checkUuidBytes(uuidLowBytes, uuidHighBytes)) {
            uuid = new UUID(uuidHighBytes, uuidLowBytes);
            LOGGER.debug("UUID from SPI: {}", uuid);
        } else {
            LOGGER.warn("No UUID in SPI");
        }
        rd.setUuid(uuid);

        // Read serial number from SPI
        String serialNumber = null;
        long sn = spiSector.getLong(0);
        if (sn != 0L && sn != -1L && sn != -4294967296L) {
            serialNumber = String.format("%014d", sn);
            LOGGER.debug("Serial Number: {}", serialNumber);
        } else {
            LOGGER.warn("No serial number in SPI");
        }
        rd.setSerialNumber(serialNumber);

        // Firmware version
        short major = -1;
        short minor = -1;
        byte[] version = new byte[14];
        sdSector.get(version);
        if ("version".equals(new String(version, StandardCharsets.UTF_16))) {
            major = sdSector.get(16);
            minor = sdSector.get(20);
            LOGGER.debug("Firmware version: {}.{}", major, minor);
        } else {
            LOGGER.warn("No firmware version");
        }
        rd.setFirmwareMinor(minor);
        rd.setFirmwareMajor(major);

        // Read card size from SD, if needed (firmware >= 1.1)
        int sdCardSizeInSectors = -1;
        if (major >= 1 && minor >= 1) {
            sdCardSizeInSectors = (sdSector.get(26) & 0xff) << 24 | (sdSector.get(27) & 0xff) << 16
                    | (sdSector.get(24) & 0xff) << 8 | sdSector.get(26) & 0xff;
        }
        // Fix card size
        if (sdCardSizeInSectors == -1) {
            sdCardSizeInSectors = SDCARD_DEFAULT_SIZE_IN_SECTORS;
        } else {
            // Account for the 10.5 MB FAT16 partition
            sdCardSizeInSectors -= SDCARD_FAT16_PARTITION_SIZE_IN_SECTORS;
        }
        // Account for the 100000 reserved sectors
        sdCardSizeInSectors -= PACK_INDEX_SD_SECTOR;
        LOGGER.debug("SD card size: {}", sdCardSizeInSectors);
        rd.setSdCardSizeInSectors(sdCardSizeInSectors);

        // Read error from SD
        short errorCode = sdSector.getShort(0);
        LOGGER.debug("Error code: {}", errorCode);
        rd.setInError(errorCode == 1);
        return rd;
    }

    @Override
    public CompletionStage<List<MetaPackDTO>> getPacksList() {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        // Read pack index
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device, this::readPackIndex)
                .thenApply(rawlist -> rawlist.stream().map(pack -> {
                    MetaPackDTO mp = new MetaPackDTO();
                    mp.setUuid(pack.getUuid().toString());
                    mp.setFormat(PackFormat.RAW.getLabel());
                    mp.setVersion(pack.getVersion());
                    mp.setSectorSize(pack.getSizeInSectors());
                    return mp;
                }).collect(Collectors.toList()));
    }

    private CompletionStage<List<RawStoryPackInfos>> readPackIndex(DeviceHandle handle) {
        return LibUsbMassStorageHelper.asyncReadSDSectors(handle, PACK_INDEX_SD_SECTOR, (short) 1)
                .thenCompose(sdPackIndexSector -> {
                    sdPackIndexSector.position(0);
                    short nbPacks = sdPackIndexSector.getShort();
                    LOGGER.debug("Number of packs in index: {}", nbPacks);

                    CompletionStage<List<RawStoryPackInfos>> promise = CompletableFuture.completedStage(new ArrayList<>());
                    for (short i = 0; i < nbPacks; i++) {
                        int startSector = sdPackIndexSector.getInt();
                        int sizeInSectors = sdPackIndexSector.getInt();
                        short statsOffset = sdPackIndexSector.getShort();
                        short samplingRate = sdPackIndexSector.getShort();
                        LOGGER.debug("Pack #{}: {} - {}", i + 1, startSector, sizeInSectors);
                        // Read version from pack's sector 0 and UUID from pack's sector 1
                        promise = promise.thenCompose(packs ->
                                LibUsbMassStorageHelper.asyncReadSDSectors(handle, PACK_INDEX_SD_SECTOR + startSector, (short) 2)
                                        .thenApply(sdPackSectors -> {
                                            short version = sdPackSectors.getShort(3);
                                            if (version == 0) {
                                                version = 1;
                                            }
                                            LOGGER.debug("Pack version: {}", version);
                                            long uuidHighBytes = sdPackSectors.getLong(LibUsbMassStorageHelper.SECTOR_SIZE);
                                            long uuidLowBytes = sdPackSectors.getLong(LibUsbMassStorageHelper.SECTOR_SIZE + 8);
                                            UUID uuid = new UUID(uuidHighBytes, uuidLowBytes);
                                            LOGGER.debug("Pack UUID: {}", uuid);
                                            RawStoryPackInfos spInfos = new RawStoryPackInfos();
                                            spInfos.setUuid(uuid);
                                            spInfos.setVersion(version);
                                            spInfos.setStartSector(startSector);
                                            spInfos.setSizeInSectors(sizeInSectors);
                                            spInfos.setStatsOffset(statsOffset);
                                            spInfos.setSamplingRate(samplingRate);
                                            packs.add(spInfos);
                                            return packs;
                                        })
                        );
                    }
                    return promise;
                });
    }

    @Override
    public CompletionStage<Boolean> reorderPacks(List<String> uuids) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device,
                handle -> readPackIndex(handle).thenCompose(packs -> {
                    // Look for UUIDs in packs index (ALL uuids must match)
                    boolean allUUIDsAreOnDevice = uuids.stream()
                            .allMatch(uuid -> packs.stream().anyMatch(p -> p.getUuid().equals(UUID.fromString(uuid))));
                    if (!allUUIDsAreOnDevice) {
                        throw new StoryTellerException("Packs on device do not match UUIDs");
                    }
                    // Reorder list according to uuids list
                    packs.sort(Comparator.comparingInt(p -> uuids.indexOf(p.getUuid().toString())));
                    // Write pack index
                    return writePackIndex(handle, packs);
                }));
    }

    @Override
    public CompletionStage<Boolean> deletePack(String uuid) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device,
                handle -> readPackIndex(handle).thenCompose(packs -> {
                    // Look for UUID in packs index
                    Optional<RawStoryPackInfos> matched = packs.stream()
                            .filter(p -> p.getUuid().equals(UUID.fromString(uuid))).findFirst();
                    if (matched.isEmpty()) {
                        throw new StoryTellerException("Pack not found");
                    }
                    RawStoryPackInfos rspi = matched.get();
                    LOGGER.debug("Found pack with uuid: {}", uuid);
                    LOGGER.debug("Matched: {} - {}", rspi.getStartSector(), rspi.getSizeInSectors());
                    // Remove from index
                    packs.remove(rspi);
                    // Write pack index
                    return writePackIndex(handle, packs);
                }));
    }

    private static CompletionStage<Boolean> writePackIndex(DeviceHandle handle, List<RawStoryPackInfos> packs) {
        // Compute packs index bytes
        ByteBuffer bb = ByteBuffer.allocateDirect(LibUsbMassStorageHelper.SECTOR_SIZE);
        bb.putShort((short) packs.size());
        for (RawStoryPackInfos pack: packs) {
            bb.putInt(pack.getStartSector());
            bb.putInt(pack.getSizeInSectors());
            bb.putShort(pack.getStatsOffset());
            bb.putShort(pack.getSamplingRate());
        }
        return LibUsbMassStorageHelper.asyncWriteSDSectors(handle, PACK_INDEX_SD_SECTOR, (short) 1, bb);
    }

    private static void followProgress(TransferProgressListener listener, TransferStatus status, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        double speed = status.getTransferred() / (elapsed / 1000.0);
        status.setSpeed(speed);
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace("Transferred {} bytes in {} ms", status.getTransferred(), elapsed);
            LOGGER.trace("Average speed = {}/sec", FileUtils.readableByteSize((long)speed));
        }
        // Call (optional) listener with transfer status
        if (listener != null) {
            CompletableFuture.runAsync(() -> listener.onProgress(status));
        }
    }

    @Override
    public CompletionStage<TransferStatus> downloadPack(String uuid, Path destPath, TransferProgressListener listener) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(device,
                handle -> readPackIndex(handle).thenCompose(packs -> {
                    // Look for UUID in packs index
                    LOGGER.debug("Search pack with uuid: {}", uuid);
                    UUID searchUUID = UUID.fromString(uuid);
                    RawStoryPackInfos rspi = packs.stream().filter(p -> searchUUID.equals(p.getUuid())).findFirst()
                            .orElseThrow();
                    LOGGER.debug("Matched: {} - {}", rspi.getStartSector(), rspi.getSizeInSectors());
                    // Keep track of transferred bytes and elapsed time
                    long startTime = System.currentTimeMillis();
                    // Copy pack chunk by chunk into the output stream
                    int totalSize = rspi.getSizeInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE;
                    var promise = CompletableFuture.completedStage(new TransferStatus(0, totalSize, 0.0));
                    for (int offset = 0; offset < rspi
                            .getSizeInSectors(); offset += PACK_TRANSFER_CHUNK_SIZE_IN_SECTORS) {
                        int sector = PACK_INDEX_SD_SECTOR + rspi.getStartSector() + offset;
                        short nbSectorsToRead = (short) Math.min(PACK_TRANSFER_CHUNK_SIZE_IN_SECTORS,
                                rspi.getSizeInSectors() - offset);
                        promise = promise.thenCompose(status -> {
                            LOGGER.trace("Reading {} bytes from device",
                                    nbSectorsToRead * LibUsbMassStorageHelper.SECTOR_SIZE);
                            return LibUsbMassStorageHelper.asyncReadSDSectors(handle, sector, nbSectorsToRead)
                                    .thenApply(read -> {
                                        // TODO Write directly from ByteBuffer to output stream ?
                                        byte[] bytes = new byte[read.remaining()];
                                        read.get(bytes);
                                        try (OutputStream fos = new BufferedOutputStream(
                                                Files.newOutputStream(destPath))) {
                                            LOGGER.trace("Writing {} bytes to output stream", bytes.length);
                                            fos.write(bytes);
                                            // Compute progress
                                            status.setTransferred(status.getTransferred() + bytes.length);
                                            followProgress(listener, status, startTime);
                                            return status;
                                        } catch (IOException e) {
                                            throw new StoryTellerException("Failed to write pack to destination file",
                                                    e);
                                        }
                                    });
                        });
                    }
                    return promise;
                }));
    }

    @Override
    public CompletionStage<TransferStatus> uploadPack(String uuid, Path inputPath, TransferProgressListener listener) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        // file size and sectors
        long packSize = 0;
        try {
            packSize = Files.size(inputPath);
        } catch (IOException e) {
            return CompletableFuture.failedStage(e);
        }
        int packSizeInSectors = (int) (packSize / LibUsbMassStorageHelper.SECTOR_SIZE);
        if(LOGGER.isInfoEnabled()) {
            LOGGER.info("Transferring pack ({}) to device: {} ({} sectors)", uuid, FileUtils.readableByteSize(packSize),
                    packSizeInSectors);
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device, handle -> 
            // Find first large-enough free space
            findFirstSuitableSector(handle, packSizeInSectors)
                    .thenCompose(startSector -> {
                        if (startSector.isEmpty()) {
                            throw new StoryTellerException("Not enough free space on the device");
                        }
                        LOGGER.debug("Adding pack at start sector: {}", startSector.get());

                        // Keep track of transferred bytes and elapsed time
                        long startTime = System.currentTimeMillis();
                        // Copy pack chunk by chunk from the input stream
                        int totalSize = packSizeInSectors * LibUsbMassStorageHelper.SECTOR_SIZE;
                        CompletionStage<TransferStatus> promise = CompletableFuture.completedStage(new TransferStatus(0, totalSize, 0.0));
                        for(int offset = 0; offset < packSizeInSectors; offset += PACK_TRANSFER_CHUNK_SIZE_IN_SECTORS) {
                            int sector = PACK_INDEX_SD_SECTOR + startSector.get() + offset;
                            short nbSectorsToWrite = (short) Math.min(PACK_TRANSFER_CHUNK_SIZE_IN_SECTORS, packSizeInSectors - offset);
                            promise = promise.thenCompose(status -> {
                                // TODO Write directly from input stream to ByteBuffer ?
                                int chunkSize = nbSectorsToWrite * LibUsbMassStorageHelper.SECTOR_SIZE;
                                ByteBuffer bb = ByteBuffer.allocateDirect(chunkSize);
                                try(InputStream input = new BufferedInputStream(Files.newInputStream(inputPath)) ) {
                                    // Read next chunk from input stream
                                    LOGGER.trace("Reading {} bytes from input stream", chunkSize);
                                    byte[] chunk = input.readNBytes(chunkSize);
                                    bb.put(chunk, 0, chunkSize);
                                    LOGGER.trace("Writing {} bytes to device", chunkSize);
                                    return LibUsbMassStorageHelper.asyncWriteSDSectors(handle, sector, nbSectorsToWrite, bb)
                                            .thenApply(written -> {
                                                // Compute progress
                                                status.setTransferred(status.getTransferred() + chunkSize);
                                                followProgress(listener, status, startTime);
                                                return status;
                                            });
                                } catch (IOException e) {
                                    throw new StoryTellerException("Failed to read pack from file", e);
                                }
                            });
                        }

                        // Rewrite packs index with added pack
                        return promise
                                .thenCompose(status -> readPackIndex(handle)
                                        .thenCompose(packs -> {
                                            RawStoryPackInfos spInfos = new RawStoryPackInfos();
                                            spInfos.setUuid(null);
                                            spInfos.setVersion((short) 0);
                                            spInfos.setStartSector(startSector.get());
                                            spInfos.setSizeInSectors(packSizeInSectors);
                                            spInfos.setStatsOffset((short) 0);
                                            spInfos.setSamplingRate((short) 0);
                                            // Add pack to index list
                                            packs.add(spInfos);
                                            // Write pack index
                                            return writePackIndex(handle, packs).thenApply(done -> status);
                                        }));
                    })
        );
    }

    private CompletionStage<Optional<Integer>> findFirstSuitableSector(DeviceHandle handle, int packSizeInSectors) {
        return readPackIndex(handle).thenCompose(packs -> {
            // Measure free spaces between and packs and return first appropriate sector
            int previousUsedSector = 0;
            int nextUsedSector;
            // Order packs by their start sector
            packs.sort(Comparator.comparingInt(RawStoryPackInfos::getStartSector));
            // Look for a large enough free space
            for (RawStoryPackInfos pack : packs) {
                nextUsedSector = pack.getStartSector();
                int freeSpace = nextUsedSector - previousUsedSector;
                if (freeSpace >= packSizeInSectors) {
                    // Free space is large enough, use it
                    return CompletableFuture.completedStage(Optional.of(previousUsedSector + 1));
                }
                previousUsedSector = pack.getStartSector() + pack.getSizeInSectors() - 1;
            }
            // Check if there is enough space after the last pack
            final int lastUsedSector = previousUsedSector;
            return readDeviceInfos(handle).thenApply(infos -> {
                int firstUnavailableSector = infos.getSdCardSizeInSectors() + 1;
                int freeSpace = firstUnavailableSector - lastUsedSector;
                if (freeSpace >= packSizeInSectors) {
                    // Free space is large enough, use it
                    return Optional.of(lastUsedSector + 1);
                }
                // There are no large-enough free space
                return Optional.empty();
            });
        });
    }

    @Override
    public CompletionStage<Void> dump(Path outputPath) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(noDevicePluggedException());
        }
        try {
            if (!Files.isDirectory(outputPath)) {
                Files.createDirectory(outputPath);
            }
        } catch (IOException e) {
            LOGGER.error("Fail to create dir", e);
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device, handle -> //
        dumpSector(handle, DEVICE_INFOS_SD_SECTOR_0, outputPath) //
                .thenCompose(d -> dumpSector(handle, DEVICE_INFOS_SD_SECTOR_2, outputPath)) //
                .thenCompose(d -> dumpSector(handle, PACK_INDEX_SD_SECTOR, outputPath)) //
                .thenCompose(d -> //
                LibUsbMassStorageHelper.asyncReadSDSectors(handle, PACK_INDEX_SD_SECTOR, (short) 1)
                        .thenCompose(sdPackIndexSector -> {
                            sdPackIndexSector.position(0);
                            short nbPacks = sdPackIndexSector.getShort();
                            LOGGER.info("Number of packs to dump: {}", nbPacks);

                            int packSector = PACK_INDEX_SD_SECTOR;
                            CompletionStage<Void> promise = CompletableFuture.completedStage(null);
                            for (short i = 0; i < nbPacks; i++) {
                                int startSector = sdPackIndexSector.getInt();
                                int sizeInSectors = sdPackIndexSector.getInt();
                                // statsOffset
                                sdPackIndexSector.getShort();
                                // samplingRate
                                sdPackIndexSector.getShort();
                                LOGGER.debug("Pack #{}: {} - {}", i + 1, startSector, sizeInSectors);
                                // Dump first, second and last sector of each pack
                                promise = promise //
                                        .thenCompose(dd -> dumpSector(handle, packSector + startSector, outputPath)) //
                                        .thenCompose(dd -> dumpSector(handle, packSector + startSector + 1, outputPath)) //
                                        .thenCompose(dd -> dumpSector(handle,
                                                packSector + startSector + sizeInSectors - 1, outputPath));
                            }
                            return promise;
                        })));
    }

    private static CompletionStage<Void> dumpSector(DeviceHandle handle, int sector, Path outputPath) {
        Path destPath = outputPath.resolve("sector" + sector + ".bin");
        LOGGER.info("Dumping sector {} into {}", sector, destPath.getFileName());
        return LibUsbMassStorageHelper.asyncReadSDSectors(handle, sector, (short) 1) //
                .thenAccept(read -> {
                    try (SeekableByteChannel sbc = Files.newByteChannel(destPath, WRITE, CREATE, TRUNCATE_EXISTING)) {
                        sbc.write(read);
                    } catch (IOException e) {
                        throw new StoryTellerException("Failed to dump sector " + sector + " from SD card.", e);
                    }
                });
    }

    private static StoryTellerException noDevicePluggedException() {
        return new StoryTellerException("No device plugged");
    }
}
