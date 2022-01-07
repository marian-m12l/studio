package studio.core.v1.writer;

import java.io.IOException;
import java.nio.file.Path;

import studio.core.v1.model.StoryPack;

public interface StoryPackWriter {

    void write(StoryPack pack, Path path, boolean enriched) throws IOException;

}
