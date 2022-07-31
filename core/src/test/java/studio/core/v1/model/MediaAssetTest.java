package studio.core.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import studio.core.v1.model.asset.MediaAssetType;

class MediaAssetTest {

    @Test
    void mediaType() {
        for (MediaAssetType e : MediaAssetType.values()) {
            assertEquals(e, MediaAssetType.fromMime(e.getMime()));
            for (String ext : e.getExtensions()) {
                assertEquals(e, MediaAssetType.fromExtension(ext));
            }
        }
        // 2nd mime for MP3
        assertEquals(MediaAssetType.MP3, MediaAssetType.fromMime("audio/mpeg"));
    }

}
