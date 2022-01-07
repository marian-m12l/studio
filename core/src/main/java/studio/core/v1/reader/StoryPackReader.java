package studio.core.v1.reader;

import java.io.IOException;
import java.nio.file.Path;

import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;

public interface StoryPackReader {

    StoryPackMetadata readMetadata(Path path) throws IOException;

    StoryPack read(Path path) throws IOException;

}
