package studio.core.v1.utils;

import java.nio.file.Files;
import java.nio.file.Path;

import studio.core.v1.reader.StoryPackReader;
import studio.core.v1.reader.archive.ArchiveStoryPackReader;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.core.v1.reader.fs.FsStoryPackReader;
import studio.core.v1.writer.StoryPackWriter;
import studio.core.v1.writer.archive.ArchiveStoryPackWriter;
import studio.core.v1.writer.binary.BinaryStoryPackWriter;
import studio.core.v1.writer.fs.FsStoryPackWriter;

public enum PackFormat {

    ARCHIVE(".zip", new ArchiveStoryPackReader(), new ArchiveStoryPackWriter()),

    RAW(".pack", new BinaryStoryPackReader(), new BinaryStoryPackWriter()),

    FS(null, new FsStoryPackReader(), new FsStoryPackWriter());

    /** Guess format from file input. */
    public static PackFormat fromPath(Path path) {
        if (path.toString().endsWith(ARCHIVE.extension)) {
            return ARCHIVE;
        } else if (path.toString().endsWith(RAW.extension)) {
            return RAW;
        } else if (Files.isDirectory(path)) {
            return FS;
        }
        return null;
    }

    private PackFormat(String extension, StoryPackReader reader, StoryPackWriter writer) {
        this.extension = extension;
        this.reader = reader;
        this.writer = writer;
    }

    private final String extension;
    private final StoryPackReader reader;
    private final StoryPackWriter writer;

    /** Lowercase for trace and json conversion */
    public String getLabel() {
        return name().toLowerCase();
    }

    public StoryPackReader getReader() {
        return reader;
    }

    public StoryPackWriter getWriter() {
        return writer;
    }

    public String getExtension() {
        return extension;
    }
}
