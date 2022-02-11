/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1;

public final class Constants {

    private Constants() {
        throw new IllegalArgumentException("Utility class");
    }

    public static final int SECTOR_SIZE = 512;

    public static final int PACKS_LIST_SECTOR2 = 100000;

    public static final int BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING = 11 + 48;
    public static final int BINARY_ENRICHED_METADATA_TITLE_TRUNCATE = 64; // 64 characters == 128 bytes
    public static final int BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE = 128; // 128 characters == 256 bytes
    public static final int BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING = 10 + 48;
    public static final int BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT = 16;
    public static final int BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING = 48;
    public static final int BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE = 64; // 64 characters == 128 bytes

}
