package studio.core.v1.model.asset;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum MediaAssetType {
    // image
    BMP("image/bmp", ".bmp"), PNG("image/png", ".png"), JPEG("image/jpeg", ".jpg", ".jpeg"),
    // audio
    WAV("audio/x-wav", ".wav"), MP3("audio/mp3", ".mp3"), OGG("audio/ogg", ".ogg", ".oga");

    private static final Map<String, MediaAssetType> BY_MIME = new HashMap<>();
    private static final Map<String, MediaAssetType> BY_EXTENSION = new HashMap<>();

    static {
        for (MediaAssetType e : values()) {
            BY_MIME.put(e.mime, e);
            for (String ext : e.extensions) {
                BY_EXTENSION.putIfAbsent(ext, e);
            }
        }
        // 2nd mime for MP3
        BY_MIME.put("audio/mpeg", MP3);
    }

    public static MediaAssetType fromExtension(String extension) {
        return BY_EXTENSION.get(extension);
    }

    public static MediaAssetType fromMime(String mime) {
        return BY_MIME.get(mime);
    }

    private final String mime;
    private final List<String> extensions;

    private MediaAssetType(String mime, String... extensions) {
        this.mime = mime;
        this.extensions = Arrays.asList(extensions);
    }

    public String getMime() {
        return mime;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public String firstExtension() {
        return extensions.get(0);
    }
}
