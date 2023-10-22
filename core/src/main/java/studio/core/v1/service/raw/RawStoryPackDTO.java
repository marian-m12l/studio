package studio.core.v1.service.raw;

import lombok.Value;

public interface RawStoryPackDTO {

    int SECTOR_SIZE = 512;

    int BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING = 11 + 48;
    int BINARY_ENRICHED_METADATA_TITLE_TRUNCATE = 64; // 64 characters == 128 bytes
    int BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE = 128; // 128 characters == 256 bytes
    int BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING = 10 + 48;
    int BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT = 16;
    int BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING = 48;
    int BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE = 64; // 64 characters == 128 bytes

    enum AssetType {
        AUDIO, IMAGE
    }

    @Value
    final class AssetAddr implements Comparable<AssetAddr> {
        private AssetType type;
        private int offset;
        private int size;

        @Override
        public int compareTo(AssetAddr o) {
            return this.offset - o.offset;
        }
    }

    @Value
    final class SectorAddr implements Comparable<SectorAddr> {
        private int offset;

        @Override
        public int compareTo(SectorAddr o) {
            return this.offset - o.offset;
        }
    }
}
