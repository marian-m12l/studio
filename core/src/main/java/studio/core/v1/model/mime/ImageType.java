package studio.core.v1.model.mime;

import java.util.Arrays;
import java.util.List;

public enum ImageType {

    BMP("image/bmp", ".bmp"), PNG("image/png", ".png"), JPEG("image/jpeg", ".jpg", ".jpeg");

    private String mime;
    private List<String> extensions;

    private ImageType(String mime, String... extensions) {
        this.mime = mime;
        this.extensions = Arrays.asList(extensions);
    }

    public static ImageType fromExtension(String extension) {
        for (ImageType e : values()) {
            if (e.extensions.contains(extension)) {
                return e;
            }
        }
        return null;
    }

    public static ImageType fromMime(String mime) {
        for (ImageType e : values()) {
            if (e.is(mime)) {
                return e;
            }
        }
        return null;
    }

    public String getMime() {
        return mime;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public boolean is(String mime) {
        return this.mime.equals(mime);
    }

}
