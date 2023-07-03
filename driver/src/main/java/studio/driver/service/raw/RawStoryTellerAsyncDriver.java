/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.driver.service.raw;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static studio.driver.service.raw.LibUsbMassStorageHelper.SECTOR_SIZE;
import static studio.core.v1.utils.io.FileUtils.dataInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
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

import org.usb4java.Device;
import org.usb4java.DeviceHandle;

import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import studio.core.v1.exception.NoDevicePluggedException;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.service.PackFormat;
import studio.core.v1.utils.io.FileUtils;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.core.v1.model.TransferListener.TransferProgressListener;
import studio.core.v1.model.TransferListener.TransferStatus;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.DeviceInfosDTO.StorageDTO;
import studio.driver.model.MetaPackDTO;
import studio.driver.model.UsbDeviceVersion;
import studio.driver.service.StoryTellerAsyncDriver;
import studio.driver.usb.LibUsbDetectionHelper;

public class RawStoryTellerAsyncDriver implements StoryTellerAsyncDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawStoryTellerAsyncDriver.class);

    static final int SDCARD_DEFAULT_SIZE_IN_SECTORS = 6815513;
    static final int SDCARD_FAT16_PARTITION_SIZE_IN_SECTORS = 20480;    // 10.5 MB
    static final int DEVICE_INFOS_SPI_OFFSET = 520192;
    static final int DEVICE_INFOS_SD_SECTOR_0 = 0;
    static final int DEVICE_INFOS_SD_SECTOR_2 = 2;
    static final int PACK_INDEX_SD_SECTOR = 100000;
    static final int PACK_TRANSFER_CHUNK_SECTOR_SIZE = 5000;    // 2.5 MB

    private Device device = null;
    private List<DevicePluggedListener> pluggedlisteners = new ArrayList<>();
    private List<DeviceUnpluggedListener> unpluggedlisteners = new ArrayList<>();

    @Getter
    @Setter
    protected static class RawStoryPackInfos {
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
        LibUsbDetectionHelper.getInstance().initializeLibUsb( //
            UsbDeviceVersion.DEVICE_VERSION_1, //
            dev -> {
                // Update device reference
                this.device = dev;
                // Notify listeners
                this.pluggedlisteners.forEach(l -> l.onDevicePlugged(dev));
            }, //
            dev -> {
                // Update device reference
                this.device = null;
                // Notify listeners
                this.unpluggedlisteners.forEach(l -> l.onDeviceUnplugged(dev));
            });
    }

    @Override
    public boolean hasDevice() {
        return device != null;
    }

    @Override
    public void registerDeviceListener(DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener) {
        pluggedlisteners.add(pluggedlistener);
        unpluggedlisteners.add(unpluggedlistener);
    }

    @Override
    public CompletionStage<DeviceInfosDTO> getDeviceInfos() {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(device, this::readDeviceInfos);
    }

    private CompletionStage<DeviceInfosDTO> readDeviceInfos(DeviceHandle handle) {
        // Read UUID and Serial Number from SPI
        return LibUsbMassStorageHelper.asyncReadSPISectors(handle, DEVICE_INFOS_SPI_OFFSET, (short) 1)
                // Read firmware version, card size and error from SD
                .thenCombine(LibUsbMassStorageHelper.asyncReadSDSectors(handle, DEVICE_INFOS_SD_SECTOR_2, (short) 1),
                        this::readRawDeviceInfos)
                // Compute used SD card space from packs index
                .thenCompose(rd -> readPackIndex(handle).thenApply(packs -> {
                    int usedSectors = packs.stream().map(RawStoryPackInfos::getSizeInSectors).reduce(0, Integer::sum);
                    rd.getStorage().setTaken(usedSectors * SECTOR_SIZE);
                    rd.getStorage().updateFree();
                    return rd;
                }));
    }

    static boolean checkUuidBytes(long lowBytes, long highBytes) {
        return Stream.of(0L, -1L, -4294967296L).allMatch(v -> lowBytes != v || highBytes != v);
    }

    static RawStoryPackInfos findPack(List<RawStoryPackInfos> packs, UUID uuid) {
        return packs.stream() //
            .filter(p-> p.getUuid().equals(uuid)).findFirst() //
            .orElseThrow(() -> new StoryTellerException("Pack not found " + uuid));
    }

    protected DeviceInfosDTO readRawDeviceInfos(ByteBuffer spiSector, ByteBuffer sdSector) {
        DeviceInfosDTO infos = new DeviceInfosDTO();
        infos.setPlugged(true);
        infos.setDriver(PackFormat.RAW.getLabel());

        // Read serial number from SPI
        long sn = spiSector.getLong(0);
        if (sn != 0L && sn != -1L && sn != -4294967296L) {
            String serialNumber = String.format("%014d", sn);
            LOGGER.debug("Serial Number: {}", serialNumber);
            infos.setSerial(serialNumber);
        } else {
            LOGGER.warn("No serial number in SPI");
        }

        // Read UUID from SPI
        long uuidLowBytes = spiSector.getLong(8); // Read low 8 bytes
        long uuidHighBytes = spiSector.getLong(16); // Read high 8 bytes
        if (checkUuidBytes(uuidLowBytes, uuidHighBytes)) {
            infos.setUuid(new UUID(uuidHighBytes, uuidLowBytes));
            LOGGER.debug("UUID from SPI: {}", infos.getUuid());
        } else {
            LOGGER.warn("No UUID in SPI");
        }

        sdSector.order(ByteOrder.LITTLE_ENDIAN);
        // Read firmware version from SD
        String version = new String(new char[]{ sdSector.getChar(0), sdSector.getChar(2), sdSector.getChar(4), //
            sdSector.getChar(6), sdSector.getChar(8), sdSector.getChar(10), sdSector.getChar(12) });
        LOGGER.debug("Firmware header '{}'", version);
        short major = -1;
        short minor = -1;
        if ("version".equals(version)) {
            major = sdSector.getShort(16);
            minor = sdSector.getShort(20);
            infos.setFirmware(major, minor);
            LOGGER.info("Firmware version: {}", infos.getFirmware());
        } else {
            LOGGER.warn("No firmware version");
        }

        // Read card size from SD, if needed (firmware >= 1.1)
        int sdCardSizeInSectors = -1;
        if (major >= 1 && minor >= 1) {
            sdCardSizeInSectors = (sdSector.get(26) & 0xff) << 24 | (sdSector.get(27) & 0xff) << 16
                    | (sdSector.get(24) & 0xff) << 8 | sdSector.get(25) & 0xff;
            LOGGER.debug("SD sector size: {}", sdCardSizeInSectors);
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
        infos.setStorage(new StorageDTO(sdCardSizeInSectors * SECTOR_SIZE, 0, 0));

        // Read error from SD
        short errorCode = sdSector.getShort(0);
        LOGGER.debug("Error code: {}", errorCode);
        infos.setError(errorCode == 1);
        return infos;
    }

    @Override
    public CompletionStage<List<MetaPackDTO>> getPacksList() {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        // Read pack index
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device, this::readPackIndex)
                .thenApply(rawlist -> rawlist.stream().map(pack -> {
                    MetaPackDTO mp = new MetaPackDTO();
                    mp.setUuid(pack.getUuid());
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
                                            long uuidHighBytes = sdPackSectors.getLong(SECTOR_SIZE);
                                            long uuidLowBytes = sdPackSectors.getLong(SECTOR_SIZE + 8);
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
    public CompletionStage<Boolean> reorderPacks(List<UUID> uuids) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device, handle -> readPackIndex(handle) //
                .thenCompose(packs -> {
                    // Look for UUIDs in packs index (ALL uuids must match)
                    var packsUuids = packs.stream().map(RawStoryPackInfos::getUuid).collect(Collectors.toUnmodifiableList());
                    if (!packsUuids.containsAll(uuids)) {
                        throw new StoryTellerException("Packs on device do not match UUIDs");
                    }
                    // Reorder list according to uuids list
                    packs.sort(Comparator.comparingInt(p -> uuids.indexOf(p.getUuid())));
                    // Write pack index
                    return writePackIndex(handle, packs);
                }));
    }

    @Override
    public CompletionStage<Boolean> deletePack(UUID uuid) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device, handle -> readPackIndex(handle) //
                .thenCompose(packs -> {
                    // Look for UUID in packs index
                    RawStoryPackInfos rspi = findPack(packs, uuid);
                    LOGGER.debug("Found pack with uuid {} (start={}, size={})", uuid, rspi.getStartSector(), rspi.getSizeInSectors());
                    // Remove from index
                    packs.remove(rspi);
                    // Write pack index
                    return writePackIndex(handle, packs);
                }));
    }

    private static CompletionStage<Boolean> writePackIndex(DeviceHandle handle, List<RawStoryPackInfos> packs) {
        // Compute packs index bytes
        ByteBuffer bb = ByteBuffer.allocateDirect(SECTOR_SIZE);
        bb.putShort((short) packs.size());
        for (RawStoryPackInfos pack: packs) {
            bb.putInt(pack.getStartSector());
            bb.putInt(pack.getSizeInSectors());
            bb.putShort(pack.getStatsOffset());
            bb.putShort(pack.getSamplingRate());
        }
        return LibUsbMassStorageHelper.asyncWriteSDSectors(handle, PACK_INDEX_SD_SECTOR, (short) 1, bb);
    }

    @Override
    public CompletionStage<UUID> downloadPack(final UUID uuid, Path destFolder, TransferProgressListener listener) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        Path destPath = destFolder.resolve(uuid.toString() + PackFormat.RAW.getExtension());
        return LibUsbMassStorageHelper.executeOnDeviceHandle(device, handle -> //
        readPackIndex(handle) //
        .thenCompose(packs -> {
            // Look for UUID in packs index
            RawStoryPackInfos rspi = findPack(packs, uuid);
            LOGGER.debug("Found pack with uuid {} (starts={}, size={})", uuid, rspi.getStartSector(), rspi.getSizeInSectors());
            // Copy pack chunk by chunk into the output stream
            int rspiSize = rspi.getSizeInSectors();
            int totalSize = rspiSize * SECTOR_SIZE;
            TransferStatus status = new TransferStatus(uuid, totalSize);
            var promise = CompletableFuture.completedFuture((UUID)null);
            for (int offset = 0; offset < rspiSize; offset += PACK_TRANSFER_CHUNK_SECTOR_SIZE) {
                int sector = PACK_INDEX_SD_SECTOR + rspi.getStartSector() + offset;
                short sectorToRead = (short) Math.min(PACK_TRANSFER_CHUNK_SECTOR_SIZE, rspiSize - offset);
                promise = promise.thenCompose(i -> {
                    LOGGER.trace("Reading {} bytes from device", sectorToRead * SECTOR_SIZE);
                    return LibUsbMassStorageHelper.asyncReadSDSectors(handle, sector, sectorToRead)
                        .thenApply(bb -> {
                            byte[] bytes = bb.array();
                            LOGGER.trace("Writing {} bytes to output stream", bytes.length);
                            try {
                                Files.write(destPath, bytes, CREATE, APPEND);
                            } catch (IOException e) {
                                throw new StoryTellerException("Failed to write to destination file", e);
                            }
                            // Compute progress
                            status.update(bytes.length);
                            listener.onProgress(status);
                            return uuid;
                        });
                });
            }
            return promise;
        }));
    }

    @Override
    public CompletionStage<UUID> uploadPack(final UUID uuid, Path srcPath, TransferProgressListener listener) {
        if (!hasDevice()) {
            return CompletableFuture.failedStage(new NoDevicePluggedException());
        }
        // file size and sectors
        long packSize = 0;
        try {
            packSize = Files.size(srcPath);
        } catch (IOException e) {
            return CompletableFuture.failedStage(e);
        }
        TransferStatus status = new TransferStatus(uuid, packSize);
        int packSizeInSectors = (int) (packSize / SECTOR_SIZE);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Transferring pack ({}) to device: {} ({} sectors)", uuid, FileUtils.readableByteSize(packSize),
                    packSizeInSectors);
        }
        return LibUsbMassStorageHelper.executeOnDeviceHandle(this.device, handle -> //
        // Find first large-enough free space
        findFirstSuitableSector(handle, packSizeInSectors) //
        .thenCompose(startSector -> {
            if (startSector.isEmpty()) {
                throw new StoryTellerException("Not enough free space on the device");
            }
            LOGGER.debug("Adding pack at start sector: {}", startSector.get());
            // Copy pack chunk by chunk from the input stream
            var promise = CompletableFuture.completedFuture((Void)null);
            for (int offset = 0; offset < packSizeInSectors; offset += PACK_TRANSFER_CHUNK_SECTOR_SIZE) {
                int sector = PACK_INDEX_SD_SECTOR + startSector.get() + offset;
                short nbSectorsToWrite = (short) Math.min(PACK_TRANSFER_CHUNK_SECTOR_SIZE, packSizeInSectors - offset);
                int chunkSize = nbSectorsToWrite * SECTOR_SIZE;
                promise = promise.thenCompose(i -> {
                    // Read next chunk from input stream
                    try (InputStream is = dataInputStream(srcPath)) {
                        LOGGER.trace("Reading {} bytes from input stream", chunkSize);
                        ByteBuffer bb = ByteBuffer.wrap(is.readNBytes(chunkSize));
                        // write to SD
                        return LibUsbMassStorageHelper.asyncWriteSDSectors(handle, sector, nbSectorsToWrite, bb)
                            // Compute progress
                            .thenAccept(b -> {
                                status.update(chunkSize);
                                listener.onProgress(status);
                            });
                    } catch (IOException e) {
                        throw new StoryTellerException("Failed to read pack from file", e);
                    }
                });
            }
            // Rewrite packs index with added pack
            return promise.thenCompose(i -> readPackIndex(handle) //
              .thenCompose(packs -> {
                RawStoryPackInfos spInfos = new RawStoryPackInfos();
                spInfos.setUuid(null); // useless for index
                spInfos.setVersion((short) 0); // useless for index
                spInfos.setStartSector(startSector.get());
                spInfos.setSizeInSectors(packSizeInSectors);
                spInfos.setStatsOffset((short) 0);
                spInfos.setSamplingRate((short) 0);
                // Add pack to index list
                packs.add(spInfos);
                // Write pack index
                return writePackIndex(handle, packs);
            })).thenApply(d -> uuid);
        }));
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
                int firstUnavailableSector = (int) (infos.getStorage().getSize() / SECTOR_SIZE + 1);
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
            return CompletableFuture.failedStage(new NoDevicePluggedException());
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

                            CompletionStage<Void> promise = CompletableFuture.completedStage(null);
                            for (short i = 0; i < nbPacks; i++) {
                                int startSector = sdPackIndexSector.getInt();
                                int sizeInSectors = sdPackIndexSector.getInt();
                                int packSector = PACK_INDEX_SD_SECTOR + startSector;
                                // statsOffset
                                sdPackIndexSector.getShort();
                                // samplingRate
                                sdPackIndexSector.getShort();
                                LOGGER.debug("Pack #{}: {} - {}", i + 1, startSector, sizeInSectors);
                                // Dump first, second and last sector of each pack
                                promise = promise //
                                        .thenCompose(dd -> dumpSector(handle, packSector, outputPath)) //
                                        .thenCompose(dd -> dumpSector(handle, packSector + 1, outputPath)) //
                                        .thenCompose(dd -> dumpSector(handle, packSector + sizeInSectors - 1, outputPath));
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
}
