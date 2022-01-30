/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ID3Tags {

    private static final Logger LOGGER = LogManager.getLogger(ID3Tags.class);

    private static final int ID3V1_SIZE = 128;
    private static final int ID3V2_HEADER_SIZE = 10;
    private static final int ID3V2_SIZE_OFFSET = 6;

    private ID3Tags() {
        throw new IllegalArgumentException("Utility class");
    }

    public static boolean hasID3v1Tag(byte[] mp3Data) {
        // Look for ID3v1 tag at end of file
        ByteBuffer mp3Buffer = ByteBuffer.wrap(mp3Data);
        mp3Buffer.position(mp3Data.length - ID3V1_SIZE);
        byte char1 = mp3Buffer.get();
        byte char2 = mp3Buffer.get();
        byte char3 = mp3Buffer.get();
        return (char1 == 0x54 && char2 == 0x41 && char3 == 0x47);   // "TAG"
    }

    public static byte[] removeID3v1Tag(byte[] mp3Data) {
        if (hasID3v1Tag(mp3Data)) {
            // Remove last 128 bytes
            LOGGER.debug("Removing ID3v1 tag at end of file (128 bytes).");
            return Arrays.copyOfRange(mp3Data, 0, mp3Data.length - ID3V1_SIZE);
        }
        return mp3Data;
    }

    public static boolean hasID3v2Tag(byte[] mp3Data) {
        // Look for ID3v2 tag at beginning of file
        ByteBuffer mp3Buffer = ByteBuffer.wrap(mp3Data);
        byte char1 = mp3Buffer.get();
        byte char2 = mp3Buffer.get();
        byte char3 = mp3Buffer.get();
        return (char1 == 0x49 && char2 == 0x44 && char3 == 0x33);   // "ID3"
    }

    public static byte[] removeID3v2Tag(byte[] mp3Data) {
        if (hasID3v2Tag(mp3Data)) {
            // Read tag size and remove first <n> bytes
            ByteBuffer mp3Buffer = ByteBuffer.wrap(mp3Data);
            mp3Buffer.position(ID3V2_SIZE_OFFSET);
            byte size1 = mp3Buffer.get();
            byte size2 = mp3Buffer.get();
            byte size3 = mp3Buffer.get();
            byte size4 = mp3Buffer.get();
            int size = ((size1 & 0x7f) << 21) | ((size2 & 0x7f) << 14) | ((size3 & 0x7f) << 7) | (size4 & 0x7f);
            size += ID3V2_HEADER_SIZE;
            LOGGER.debug("Removing ID3v2 tag at beginning of file ({} bytes).", size);
            return Arrays.copyOfRange(mp3Data, size, mp3Data.length);
        }
        return mp3Data;
    }
}
