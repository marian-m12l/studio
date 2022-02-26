package studio.core.v1.model.asset;

import java.util.Arrays;
import java.util.List;

public enum AudioType {

    WAV("audio/x-wav", ".wav"), MPEG("audio/mpeg", ".mp3"), MP3("audio/mp3", ".mp3"), OGG("audio/ogg", ".ogg", ".oga");

    private final String mime;
    private final List<String> extensions;

    private AudioType(String mime, String... extensions) {
        this.mime = mime;
        this.extensions = Arrays.asList(extensions);
    }

    public static AudioType fromExtension(String extension) {
        for (AudioType e : values()) {
            if (e.extensions.contains(extension)) {
                return e;
            }
        }
        return null;
    }

    public static AudioType fromMime(String mime) {
        for (AudioType e : values()) {
            if (e.mime.equals(mime)) {
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

    public String getFirstExtension() {
        return extensions.get(0);
    }
}
