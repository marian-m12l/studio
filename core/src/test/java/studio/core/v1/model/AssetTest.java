package studio.core.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import studio.core.v1.model.asset.AudioType;
import studio.core.v1.model.asset.ImageType;

class AssetTest {

    @Test
    void audioType() {
        for (AudioType e : AudioType.values()) {
            assertEquals(e, AudioType.fromMime(e.getMime()));
            for (String ext : e.getExtensions()) {
                assertEquals(e, AudioType.fromExtension(ext));
            }
        }
        // 2nd mime for MP3
        assertEquals(AudioType.MP3, AudioType.fromMime("audio/mpeg"));
    }

    @Test
    void imageType() {
        for (ImageType e : ImageType.values()) {
            assertEquals(e, ImageType.fromMime(e.getMime()));
            for (String ext : e.getExtensions()) {
                assertEquals(e, ImageType.fromExtension(ext));
            }
        }
    }
}
