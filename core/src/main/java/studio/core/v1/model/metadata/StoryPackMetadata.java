/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.metadata;

public class StoryPackMetadata {

    private String format;
    private String uuid;
    private short version;
    private String title;
    private String description;
    private byte[] thumbnail;
    private Integer sectorSize;
    private boolean nightModeAvailable = false;

    public StoryPackMetadata() {
    }

    public StoryPackMetadata(String format) {
        this.format = format;
    }

    public StoryPackMetadata(String format, String uuid, short version, String title, String description, byte[] thumbnail, Integer sectorSize, boolean nightModeAvailable) {
        this.format = format;
        this.uuid = uuid;
        this.version = version;
        this.title = title;
        this.description = description;
        this.thumbnail = thumbnail;
        this.sectorSize = sectorSize;
        this.nightModeAvailable = nightModeAvailable;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public short getVersion() {
        return version;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Integer getSectorSize() {
        return sectorSize;
    }

    public void setSectorSize(Integer sectorSize) {
        this.sectorSize = sectorSize;
    }

    public boolean isNightModeAvailable() {
        return nightModeAvailable;
    }

    public void setNightModeAvailable(boolean nightModeAvailable) {
        this.nightModeAvailable = nightModeAvailable;
    }
}
