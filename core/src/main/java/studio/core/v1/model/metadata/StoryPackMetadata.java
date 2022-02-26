/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.metadata;

import studio.core.v1.utils.PackFormat;

public class StoryPackMetadata {

    private final PackFormat format;
    private String uuid;
    private short version;
    private String title;
    private String description;
    private byte[] thumbnail;
    private Integer sectorSize;
    private boolean nightModeAvailable = false;

    public StoryPackMetadata(PackFormat format) {
        this.format = format;
    }

    public PackFormat getFormat() {
        return format;
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
