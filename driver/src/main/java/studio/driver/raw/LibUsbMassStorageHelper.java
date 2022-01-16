/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.raw;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.usb4java.Device;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;
import org.usb4java.Transfer;

import studio.core.v1.utils.SecurityUtils;
import studio.driver.StoryTellerException;

/**
 * Helper methods to manipulate the Story Teller device via USB Mass Storage Bulk-Only protocol with vendor-specific
 * SCSI commands, using libusb library (http://usb4java.org/quickstart/libusb.html).
 *
 *
 * Output from lsusb -vvv:
 *      Bus 001 Device 013: ID 0c45:6820 Microdia
 *      Device Descriptor:
 *        bLength                18
 *        bDescriptorType         1
 *        bcdUSB               2.00
 *        bDeviceClass            0 (Defined at Interface level)
 *        bDeviceSubClass         0
 *        bDeviceProtocol         0
 *        bMaxPacketSize0        64
 *        idVendor           0x0c45 Microdia
 *        idProduct          0x6820
 *        bcdDevice            1.00
 *        iManufacturer           0
 *        iProduct                1 USB2.0 DSP
 *        iSerial                 1 USB2.0 DSP
 *        bNumConfigurations      1
 *        Configuration Descriptor:
 *          bLength                 9
 *          bDescriptorType         2
 *          wTotalLength           32
 *          bNumInterfaces          1
 *          bConfigurationValue     1
 *          iConfiguration          0
 *          bmAttributes         0xc0
 *            Self Powered
 *          MaxPower              500mA
 *          Interface Descriptor:
 *            bLength                 9
 *            bDescriptorType         4
 *            bInterfaceNumber        0
 *            bAlternateSetting       0
 *            bNumEndpoints           2
 *            bInterfaceClass         8 Mass Storage
 *            bInterfaceSubClass      6 SCSI
 *            bInterfaceProtocol     80 Bulk-Only
 *            iInterface              0
 *            Endpoint Descriptor:
 *              bLength                 7
 *              bDescriptorType         5
 *              bEndpointAddress     0x81  EP 1 IN
 *              bmAttributes            2
 *                Transfer Type            Bulk
 *                Synch Type               None
 *                Usage Type               Data
 *              wMaxPacketSize     0x0200  1x 512 bytes
 *              bInterval               0
 *            Endpoint Descriptor:
 *              bLength                 7
 *              bDescriptorType         5
 *              bEndpointAddress     0x02  EP 2 OUT
 *              bmAttributes            2
 *                Transfer Type            Bulk
 *                Synch Type               None
 *                Usage Type               Data
 *              wMaxPacketSize     0x0200  1x 512 bytes
 *              bInterval               0
 *      Device Qualifier (for other device speed):
 *        bLength                10
 *        bDescriptorType         6
 *        bcdUSB               2.00
 *        bDeviceClass            0 (Defined at Interface level)
 *        bDeviceSubClass         0
 *        bDeviceProtocol         0
 *        bMaxPacketSize0        64
 *        bNumConfigurations      1
 *      Device Status:     0x0001
 *        Self Powered
 *
 *
 * Mass Storage Bulk-Only specification: https://www.usb.org/sites/default/files/usbmassbulk_10.pdf
 *
 *
 * Mass Storage Bulk-Only flow charts:
 *
 *      Transfer to device:
 *          Host                Device
 *          ----                ------
 *               ----- CBW --->
 *               -- Data OUT ->
 *               <---- CSW ----
 *
 *      Transfer from device:
 *          Host                Device
 *          ----                ------
 *               ----- CBW --->
 *               <-- Data IN --
 *               <---- CSW ----
 *
 *
 * Mass Storage Bulk-Only Command Block Wrapper (CBW): 31 bytes
 *      Bytes 0-3           FIXED ("USBC")
 *                              Signature that helps identify this data packet as a CBW.  The signature field shall contain the value 43425355h (little endian), indicating a CBW
 *      Bytes 4-7           RANDOM
 *                              A Command Block Tag sent by the host.  The device shall echo the contents of this field back to the host in the dCSWTag field of the associated CSW.  The dCSWTag positively associates a CSW with the corresponding CBW.
 *      Bytes 8-11          DATA-DEPENDENT
 *                              The number of bytes of data that the host expects to transfer on the Bulk-In or Bulk-Out endpoint (as indicated by the Direction bit) during the execution of this command.
 *      Byte  12            FLAGS (0x80 for IN, 0x00 for OUT)
 *                              The bits of this field are defined as follows:
 *                                  Bit 7       Direction       0 = Data-Out from host to the device, 1 = Data-In from the device to the host.
 *                                  Bit 6       Obsolete.       The host shall set this bit to zero.
 *                                  Bits 5..0   Reserved        the host shall set these bits to zero
 *      Byte  13            FIXED (0x00)
 *                              The device Logical Unit Number (LUN) to which the command block is being sent. For devices that support multiple LUNs, the host shall place into this field the LUN to which this command block is addressed. Otherwise, the host shall set this field to zero.
 *                              First 4 bits are reserved (0)
 *      Byte  14            COMMAND-DEPENDENT (always maximum value (0x10) for Story Teller SCSI commands)
 *                              The valid length of the CBWCB in bytes.  This defines the valid length of the command block.  The only legal values are 1 through 16 (01h through 10h).  All other values are reserved.
 *                              First 3 bits are reserved (0)
 *      Byte  15-30         COMMAND-DEPENDENT
 *                              CBWBC: The command block to be executed by the device.  The device shall interpret the first bCBWCBLength bytes in this field as a command block as defined by the command set identified by bInterfaceSubClass.
 *
 *
 * Mass Storage Bulk-Only Command Status Wrapper (CSW): 13 bytes
 *      Bytes 0-3           FIXED ("USBS")
 *                              Signature that helps identify this data packet as a CSW.  The signature field shall contain the value 53425355h (little endian), indicating CSW
 *      Bytes 4-7           CBW-DEPENDENT
 *                              The device shall set this field to the value received in the dCBWTag of the associated CBW
 *      Bytes 8-11          COMMAND-RESULT
 *                              For Data-Out the device shall report in the dCSWDataResidue the difference between the amount of data expected as stated in the dCBWDataTransferLength, and the actual amount of data processed by the device. For Data-In the device shall report in the dCSWDataResidue the difference between the amount of data expected as stated in the dCBWDataTransferLength and the actual amount of relevant data sent by the device.  The dCSWDataResidue shall not exceed the value sent in the dCBWDataTransferLength.
 *      Byte  12            COMMAND-RESULT
 *                              bCSWStatus indicates the success or failure of the command.  The device shall set this byte to zero if the command completed successfully.  A non-zero value shall indicate a failure during command execution according to the following table:
 *                                  00h             Command Passed ("good status")
 *                                  01h             Command Failed
 *                                  02h             Phase Error
 *                                  03h and 04h     Reserved (Obsolete)
 *                                  05h to FFh      Reserved
 *
 *
 * Vendor-specific SCSI commands for Story Teller:
 *      Read from SPI:          0xf6 0x05 0x06 + offset (4 bytes) + number of sectors to read (2 bytes) + zero-padding
 *      Write to SPI:           0xf6 0x06 0x06 + offset (4 bytes) + number of sectors to read (2 bytes) + zero-padding
 *      Read from SD:           0xf6 0xe1 0x00 + sector number/address (4 bytes) + number of sectors to read (2 bytes) + zero-padding
 *      Write to SD :           0xf6 0xe2 0x00 + sector number/address (4 bytes) + number of sectors to read (2 bytes) + zero-padding
 *      Read status register:   0xf6 0x24 0x00 + zero-padding
 *      Write status register:  0xf6 0x21 0x00 + value (1 byte) + zero-padding
 *      Erase SPI sector:       0xf6 0x15 0x06 + start sector (2 bytes) + end sector (2 bytes) + zero-padding
 *
 */
public class LibUsbMassStorageHelper {

    private static final Logger LOGGER = Logger.getLogger(LibUsbMassStorageHelper.class.getName());

    // USB device
    private static final short INTERFACE_ID = 0;
    private static final byte ENDPOINT_IN = (byte)0x81;
    private static final byte ENDPOINT_OUT = (byte)0x02;
    private static final long TIMEOUT = 5000L;

    // Mass storage Command Block Wrapper (CBW)
    private static final int MASS_STORAGE_CBW_LENGTH = 31;
    private static final byte[] MASS_STORAGE_CBW_SIGNATURE = new byte[] { 0x55, 0x53, 0x42, 0x43 }; // "USBC"
    private static final byte[] MASS_STORAGE_CBW_DIRECTION_IN = new byte[] { (byte) 0x80 };
    private static final byte[] MASS_STORAGE_CBW_DIRECTION_OUT = new byte[] { 0x00 };
    private static final byte[] MASS_STORAGE_CBW_LUN_0 = new byte[] { 0x00 };
    private static final byte[] MASS_STORAGE_CBW_COMMAND_BLOCK_SIZE = new byte[] { 0x10 };
    private enum CBWDirection { OUTBOUND, INBOUND }

    // Mass storage Command Status Wrapper (CSW)
    private static final int MASS_STORAGE_CSW_LENGTH = 13;
    private static final byte[] MASS_STORAGE_CSW_SIGNATURE = new byte[] { 0x55, 0x53, 0x42, 0x53 }; // "USBS"

    // Vendor-specific SCSI commands
    private static final byte[] SCSI_COMMAND_CODE_READ_FROM_SPI = new byte[] { (byte) 0xf6, 0x05, 0x06 };
    private static final byte[] SCSI_COMMAND_CODE_READ_FROM_SD = new byte[] { (byte) 0xf6, (byte) 0xe1, 0x00 };
    private static final byte[] SCSI_COMMAND_CODE_WRITE_TO_SD = new byte[] { (byte) 0xf6, (byte) 0xe2, 0x00 };

    public static final short SECTOR_SIZE = 512;

    // To generate random packet tags
    private static final SecureRandom prng = new SecureRandom();

    /**
     * Execute the given function on a libusb handle for the given device. Opens and frees the handle and interface.
     * @param device The device for which to open a handle
     * @param func The function to execute on the device handle
     * @param <T> The type returned by the function
     * @return The return value from the function
     */
    public static <T> CompletableFuture<T> executeOnDeviceHandle(Device device, Function<DeviceHandle, CompletableFuture<T>> func) {
        return CompletableFuture.supplyAsync(() -> {
            // Open device handle
            DeviceHandle handle = new DeviceHandle();
            int result = LibUsb.open(device, handle);
            if (result != LibUsb.SUCCESS) {
                throw new StoryTellerException("Unable to open libusb device", new LibUsbException(result));
            }
            // First, detach kernel driver
            result = LibUsb.detachKernelDriver(handle, INTERFACE_ID);
            if (result != LibUsb.SUCCESS && result != LibUsb.ERROR_NOT_SUPPORTED && result != LibUsb.ERROR_NOT_FOUND) {
                throw new StoryTellerException("Unable to detach libusb kernel driver", new LibUsbException(result));
            }
            // Claim interface
            result = LibUsb.claimInterface(handle, INTERFACE_ID);
            if (result != LibUsb.SUCCESS) {
                throw new StoryTellerException("Unable to claim libusb interface", new LibUsbException(result));
            }
            return handle;
        })
                .thenCompose(handle -> func.apply(handle)
                        // Handler is executed in another thread to avoid deadlock (otherwise it would be called by the libusb async event handleing worker thread)
                        .whenCompleteAsync((retval, e) -> {
                            // Free interface
                            int result = LibUsb.releaseInterface(handle, INTERFACE_ID);
                            if (result != LibUsb.SUCCESS) {
                                throw new StoryTellerException("Unable to release interface", new LibUsbException(result));
                            }
                            // Close handle
                            LibUsb.close(handle);
                        })
                );
    }

    public static CompletableFuture<ByteBuffer> asyncReadSPISectors(DeviceHandle handle, int offset, short nbSectorsToRead) {
        // Write Command Block Wrapper
        ByteBuffer cbw = createSPIReadCBW(offset, nbSectorsToRead);
        return asyncTransferOut(handle, cbw)
                .thenCompose(done -> {
                    // Read data
                    ByteBuffer data = ByteBuffer.allocateDirect(nbSectorsToRead * SECTOR_SIZE);
                    return asyncTransferIn(handle, data)
                            .thenCompose(dataRead -> {
                                // Read Command Status Wrapper
                                ByteBuffer csw = ByteBuffer.allocateDirect(MASS_STORAGE_CSW_LENGTH);
                                return asyncTransferIn(handle, csw)
                                        .thenApply(cswRead -> {
                                            // Check CSW
                                            if (!checkCommandStatusWrapper(csw)) {
                                                LOGGER.severe("Read operation failed while reading from SPI");
                                                throw new StoryTellerException("Read operation failed while reading from SPI");
                                            }
                                            return data;
                                        });
                            });
                });
    }

    public static CompletableFuture<ByteBuffer> asyncReadSDSectors(DeviceHandle handle, int sector, short nbSectorsToRead) {
        // Write Command Block Wrapper
        ByteBuffer cbw = createSDReadCBW(sector, nbSectorsToRead);
        return asyncTransferOut(handle, cbw)
                .thenCompose(done -> {
                    // Read data
                    ByteBuffer data = ByteBuffer.allocateDirect(nbSectorsToRead * SECTOR_SIZE);
                    return asyncTransferIn(handle, data)
                            .thenCompose(dataRead -> {
                                // Read Command Status Wrapper
                                ByteBuffer csw = ByteBuffer.allocateDirect(MASS_STORAGE_CSW_LENGTH);
                                return asyncTransferIn(handle, csw)
                                        .thenApply(cswRead -> {
                                            // Check CSW
                                            if (!checkCommandStatusWrapper(csw)) {
                                                LOGGER.severe("Read operation failed while reading from SD");
                                                throw new StoryTellerException("Read operation failed while reading from SD");
                                            }
                                            return data;
                                        });
                            });
                });
    }

    public static CompletableFuture<Boolean> asyncWriteSDSectors(DeviceHandle handle, int sector, short nbSectorsToWrite, ByteBuffer data) {
        ByteBuffer cbw = createSDWriteCBW(sector, nbSectorsToWrite);
        return asyncTransferOut(handle, cbw)
                .thenCompose(done -> {
                    // Write data
                    return asyncTransferOut(handle, data)
                            .thenCompose(dataWritten -> {
                                // Read Command Status Wrapper
                                ByteBuffer csw = ByteBuffer.allocateDirect(MASS_STORAGE_CSW_LENGTH);
                                return asyncTransferIn(handle, csw)
                                        .thenApply(cswRead -> {
                                            // Check CSW
                                            if (!checkCommandStatusWrapper(csw)) {
                                                LOGGER.severe("Read operation failed while writing to SD");
                                                throw new StoryTellerException("Read operation failed while writing to SD");
                                            }
                                            return dataWritten;
                                        });
                            });
                });
    }

    private static CompletableFuture<Boolean> asyncTransferOut(DeviceHandle handle, ByteBuffer data) {
        CompletableFuture<Boolean> promise = new CompletableFuture<>();

        Transfer transfer = LibUsb.allocTransfer();
        LibUsb.fillBulkTransfer(
                transfer,
                handle,
                ENDPOINT_OUT,
                data,
                xfer -> {
                    if (xfer.status() != LibUsb.TRANSFER_COMPLETED) {
                        LOGGER.severe("TRANSFER OUT NOT COMPLETED: " + xfer.status());
                        promise.completeExceptionally(new StoryTellerException("Transfer OUT failed"));
                    } else {
                        LOGGER.finest("Async transfer OUT done. " + xfer.actualLength() + " bytes sent.");
                        LibUsb.freeTransfer(xfer);
                        promise.complete(true);
                    }
                },
                null,
                TIMEOUT
        );
        int result = LibUsb.submitTransfer(transfer);
        if (result != LibUsb.SUCCESS) {
            throw new StoryTellerException("Unable to submit transfer OUT", new LibUsbException(result));
        }

        return promise;
    }

    private static CompletableFuture<Boolean> asyncTransferIn(DeviceHandle handle, ByteBuffer data) {
        CompletableFuture<Boolean> promise = new CompletableFuture<>();

        Transfer transfer = LibUsb.allocTransfer();
        LibUsb.fillBulkTransfer(
                transfer,
                handle,
                ENDPOINT_IN,
                data,
                xfer -> {
                    if (xfer.status() != LibUsb.TRANSFER_COMPLETED) {
                        LOGGER.severe("TRANSFER IN NOT COMPLETED: " + xfer.status());
                        promise.completeExceptionally(new StoryTellerException("Transfer IN failed"));
                    } else {
                        LOGGER.finest("Async transfer IN done. " + xfer.actualLength() + " bytes received.");
                        LibUsb.freeTransfer(xfer);
                        promise.complete(true);
                    }
                },
                null,
                TIMEOUT
        );
        int result = LibUsb.submitTransfer(transfer);
        if (result != LibUsb.SUCCESS) {
            throw new StoryTellerException("Unable to submit transfer IN", new LibUsbException(result));
        }

        return promise;
    }

    private static ByteBuffer createSPIReadCBW(int offset, short nbSectorsToRead) {
        int expectedByteLength = nbSectorsToRead * SECTOR_SIZE;
        ByteBuffer bb = createCommandBlockWrapper(CBWDirection.INBOUND, expectedByteLength);
        // SCSI vendor-specific command to read from SPI
        bb.put(SCSI_COMMAND_CODE_READ_FROM_SPI);
        // The following values are big-endian
        bb.order(ByteOrder.BIG_ENDIAN);
        // Offset to read from (4 bytes)
        bb.putInt(offset);
        // Number of sectors to read (2 bytes)
        bb.putShort(nbSectorsToRead);
        // Remaining 7 bytes are padded with zeros
        return bb;
    }
    private static ByteBuffer createSDReadCBW(int sector, short nbSectorsToRead) {
        int expectedByteLength = nbSectorsToRead * SECTOR_SIZE;
        ByteBuffer bb = createCommandBlockWrapper(CBWDirection.INBOUND, expectedByteLength);
        // SCSI vendor-specific command to read from SD
        bb.put(SCSI_COMMAND_CODE_READ_FROM_SD);
        // The following values are big-endian
        bb.order(ByteOrder.BIG_ENDIAN);
        // Sector to read from (4 bytes)
        bb.putInt(sector);
        // Number of sectors to read (2 bytes)
        bb.putShort(nbSectorsToRead);
        // Remaining 7 bytes are padded with zeros
        return bb;
    }
    private static ByteBuffer createSDWriteCBW(int sector, short nbSectorsToWrite) {
        int dataLength = nbSectorsToWrite * SECTOR_SIZE;
        ByteBuffer bb = createCommandBlockWrapper(CBWDirection.OUTBOUND, dataLength);
        // SCSI vendor-specific command to write to SD
        bb.put(SCSI_COMMAND_CODE_WRITE_TO_SD);
        // The following values are big-endian
        bb.order(ByteOrder.BIG_ENDIAN);
        // Sector to write to (4 bytes)
        bb.putInt(sector);
        // Number of sectors to write (2 bytes)
        bb.putShort(nbSectorsToWrite);
        // Remaining 7 bytes are padded with zeros
        return bb;
    }
    private static ByteBuffer createCommandBlockWrapper(CBWDirection direction, int dataLength) {
        ByteBuffer bb = ByteBuffer.allocateDirect(MASS_STORAGE_CBW_LENGTH);
        // CBW signature (4 bytes)
        bb.put(MASS_STORAGE_CBW_SIGNATURE);
        // Random Command Block Tag (4 bytes)
        byte[] random = new byte[4];
        prng.nextBytes(random);
        bb.put(random);
        // Expected number of bytes (to read or write) (4 bytes)
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(dataLength);
        // Direction
        bb.put((byte) (direction.ordinal() << 7));
        // Logical Unit Number (LUN) to which the command block is being sent
        bb.put(MASS_STORAGE_CBW_LUN_0);
        // Length of the Command Block (all our commands are 16 bytes long)
        bb.put(MASS_STORAGE_CBW_COMMAND_BLOCK_SIZE);
        return bb;
    }

    /**
     * Checks that the Command Status Wrapper (CSW) is well-formatted and is successful.
     * @param csw The Command Status Wrapper bytes
     * @return true if the CSW is successful
     */
    private static boolean checkCommandStatusWrapper(ByteBuffer csw) {
        // Check Command Status Wrapper length
        if (csw.remaining() != MASS_STORAGE_CSW_LENGTH) {
            if(LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.severe("Invalid CSW: wrong size ("+csw.remaining()+")");
            }
            return false;
        }
        // Check CSW signature
        byte[] signature = new byte[MASS_STORAGE_CSW_SIGNATURE.length];
        csw.get(signature);
        if (!Arrays.equals(signature, MASS_STORAGE_CSW_SIGNATURE)) {
            if(LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.severe("Invalid CSW: wrong signature ("+ SecurityUtils.encodeHex(signature) +")");
            }
            return false;
        }
        // Check Command Block Tag
        /*byte[] tag = new byte[4];
        csw.get(tag);
        if (!Arrays.equals(tag, expectedTag)) {
            return false;
        }*/
        // Check residue
        csw.order(ByteOrder.LITTLE_ENDIAN);
        int residue = csw.getInt(8);
        if (residue > 0) {
            if(LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.severe("Invalid CSW: positive residue ("+residue+")");
            }
            return false;
        }
        // Check status
        byte status = csw.get(12);
        LOGGER.finest("CSW status: "+status);
        return status == 0;
    }
}
