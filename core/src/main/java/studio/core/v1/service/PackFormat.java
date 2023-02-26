package studio.core.v1.service;

import java.nio.file.Path;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import studio.core.v1.service.archive.ArchiveStoryPackReader;
import studio.core.v1.service.archive.ArchiveStoryPackWriter;
import studio.core.v1.service.fs.FsStoryPackDTO.FsStoryPack;
import studio.core.v1.service.fs.FsStoryPackReader;
import studio.core.v1.service.fs.FsStoryPackWriter;
import studio.core.v1.service.raw.RawStoryPackReader;
import studio.core.v1.service.raw.RawStoryPackWriter;

@Getter
@RequiredArgsConstructor
public enum PackFormat {

    ARCHIVE(".zip", new ArchiveStoryPackReader(), new ArchiveStoryPackWriter()),

    RAW(".pack", new RawStoryPackReader(), new RawStoryPackWriter()),

    FS("", new FsStoryPackReader(), new FsStoryPackWriter());

    /** Guess format from file input. */
    public static PackFormat fromPath(Path path) {
        if (path.toString().endsWith(ARCHIVE.extension)) {
            return ARCHIVE;
        } else if (path.toString().endsWith(RAW.extension)) {
            return RAW;
        } else if (FsStoryPack.isValid(path)) {
            return FS;
        }
        return null;
    }

    private final String extension;
    private final StoryPackReader reader;
    private final StoryPackWriter writer;

    /** Lowercase for trace and json conversion */
    public String getLabel() {
        return name().toLowerCase();
    }
}
