package studio.core.v1.model.asset;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum ImageType {

    BMP("image/bmp", ".bmp"), PNG("image/png", ".png"), JPEG("image/jpeg", ".jpg", ".jpeg");

    private static final Map<String, ImageType> BY_MIME = new HashMap<>();
    private static final Map<String, ImageType> BY_EXTENSION = new HashMap<>();

    static {
        for (ImageType e : values()) {
            BY_MIME.put(e.mime, e);
            for (String ext : e.extensions) {
                BY_EXTENSION.put(ext, e);
            }
        }
    }

    public static ImageType fromExtension(String extension) {
        return BY_EXTENSION.get(extension);
    }

    public static ImageType fromMime(String mime) {
        return BY_MIME.get(mime);
    }

    private final String mime;
    private final List<String> extensions;

    private ImageType(String mime, String... extensions) {
        this.mime = mime;
        this.extensions = Arrays.asList(extensions);
    }

    public String getMime() {
        return mime;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public String getFirstExtension() {
        return extensions.get(0);
    }
}
