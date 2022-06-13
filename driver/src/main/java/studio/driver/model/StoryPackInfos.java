package studio.driver.model;

import java.util.UUID;

import lombok.Data;

@Data
public abstract class StoryPackInfos {
    private UUID uuid;
    private short version;
}
