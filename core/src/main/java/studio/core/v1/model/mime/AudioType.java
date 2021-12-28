package studio.core.v1.model.mime;

import java.util.Arrays;
import java.util.List;

public enum AudioType {

    WAV("audio/x-wav", ".wav"), MPEG("audio/mpeg", ".mp3"), MP3("audio/mp3", ".mp3"), OGG("audio/ogg", ".ogg", ".oga");

    private String mime;
    private List<String> extensions;

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
