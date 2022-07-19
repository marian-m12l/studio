package studio.core.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

class StoryPackJsonTest {

    private ObjectMapper om = new ObjectMapper().setSerializationInclusion(Include.NON_NULL);

    static Path classpathResource(String relative) throws URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Path.of(classLoader.getResource(relative).toURI());
    }

    @Test
    void readWriteJson() throws StreamReadException, DatabindException, IOException, URISyntaxException {
        Path sp1 = classpathResource("story_test.json");
        Path sp2 = sp1.resolveSibling("story_test2.json");
        Path sp3 = sp1.resolveSibling("story_test3.json");

        StoryPack storyPack = om.readValue(sp1.toFile(), StoryPack.class);
        om.writerWithDefaultPrettyPrinter().writeValue(sp2.toFile(), storyPack);

        StoryPack storyPack2 = om.readValue(sp2.toFile(), StoryPack.class);
        om.writerWithDefaultPrettyPrinter().writeValue(sp3.toFile(), storyPack2);

        assertEquals(Files.readString(sp2), Files.readString(sp3), "Files differ");
    }

}
