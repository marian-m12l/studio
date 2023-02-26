package studio.driver.service.raw;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.driver.service.raw.LibUsbMassStorageHelper.SECTOR_SIZE;
import static studio.driver.service.raw.RawStoryTellerAsyncDriver.PACK_INDEX_SD_SECTOR;
import static studio.driver.service.raw.RawStoryTellerAsyncDriver.SDCARD_FAT16_PARTITION_SIZE_IN_SECTORS;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import studio.core.v1.service.PackFormat;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.DeviceInfosDTO.StorageDTO;

class RawStoryTellerAsyncDriverTests {

    private static boolean checkUuidBytes(long highBytes, long lowBytes) {
        return (highBytes != 0L || lowBytes != 0L) && (highBytes != -1L || lowBytes != -1L)
                && (lowBytes != -4294967296L || highBytes != -4294967296L);
    }

    @Test
    void validBytes() {
        List<Long> values = Arrays.asList(-4294967296L, -2L, -1L, 0L, 1L, 2L, 42L);
        for (Long i : values) {
            for (Long j : values) {
                assertEquals(checkUuidBytes(i, j), RawStoryTellerAsyncDriver.checkUuidBytes(i, j),
                        String.format("checkUuidBytes(%d,%d)", i, j));
            }
        }
    }

    @Test
    void endian() throws IOException {
        long bigEndianLong = 12345678L;
        short littleEndianShort = 6789;
        ByteBuffer bb = ByteBuffer.allocate(32);
        // BigEndian by default
        bb.putLong(bigEndianLong);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(littleEndianShort);
        try(DataInputStream is = new DataInputStream(new ByteArrayInputStream(bb.array()))) {
          long l1 = is.readLong();
          short s1 = Short.reverseBytes(is.readShort());
          assertAll("Endian", //
             () -> assertEquals(bigEndianLong, l1), //
             () -> assertEquals(littleEndianShort, s1) //
          );
       }
    }

    @Test
    void readDeviceInfos() {
        ByteBuffer spi = ByteBuffer.allocate(512);
        // serial
        spi.putLong(11111l);
        // uuid
        spi.putLong(4444l);
        spi.putLong(2222l);

        ByteBuffer sd = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        // [00-16[ : label
        sd.putChar('v');
        sd.putChar('e');
        sd.putChar('r');
        sd.putChar('s');
        sd.putChar('i');
        sd.putChar('o');
        sd.putChar('n');
        sd.putChar(':');
        // [16-24[ : fw value
        sd.putInt(1);
        sd.putInt(2);
        // [24-28[ : sdCardSizeInSectors (fw >= 1.1)
        int size = 6815513;
        sd.put((byte)(size >>> 8));
        sd.put((byte)(size >>> 0));
        sd.put((byte)(size >>> 24));
        sd.put((byte)(size >>> 16));

        DeviceInfosDTO rdExp = new DeviceInfosDTO();
        rdExp.setDriver(PackFormat.RAW.getLabel());
        rdExp.setFirmware((short)1,(short)2);
        rdExp.setSerial(String.format("%014d", 11111l));
        rdExp.setUuid(new UUID(2222l, 4444l));
        long sdSize = SECTOR_SIZE * (size - SDCARD_FAT16_PARTITION_SIZE_IN_SECTORS - PACK_INDEX_SD_SECTOR);
        rdExp.setStorage(new StorageDTO(sdSize, 0, 0));

        var rsd = new RawStoryTellerAsyncDriver();
        DeviceInfosDTO rdAct = rsd.readRawDeviceInfos(spi, sd);
        assertAll( "DeviceInfos", //
            () -> assertEquals(rdExp.getDriver(), rdAct.getDriver()), //
            () -> assertEquals(rdExp.getFirmware(), rdAct.getFirmware()), //
            () -> assertEquals(rdExp.getSerial(), rdAct.getSerial()), //
            () -> assertEquals(rdExp.getUuid(), rdAct.getUuid()), //
            () -> assertEquals(rdExp.getStorage().getSize(), rdAct.getStorage().getSize()), //
            () -> assertEquals(rdExp.getStorage().getTaken(), rdAct.getStorage().getTaken()), //
            () -> assertEquals(rdExp.getStorage().getFree(), rdAct.getStorage().getFree()) //
        );
    }
}
