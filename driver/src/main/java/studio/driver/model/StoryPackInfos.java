package studio.driver.model;

import java.util.UUID;

public abstract class StoryPackInfos {

    private UUID uuid;
    private short version;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public short getVersion() {
        return version;
    }

    public void setVersion(short version) {
        this.version = version;
    }

}
