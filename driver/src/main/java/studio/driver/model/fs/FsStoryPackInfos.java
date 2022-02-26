/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.fs;

import studio.driver.model.StoryPackInfos;

public class FsStoryPackInfos extends StoryPackInfos {

    private String folderName;
    private long sizeInBytes;
    private boolean nightModeAvailable;

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public boolean isNightModeAvailable() {
        return nightModeAvailable;
    }

    public void setNightModeAvailable(boolean nightModeAvailable) {
        this.nightModeAvailable = nightModeAvailable;
    }

    @Override
    public String toString() {
        return "FsStoryPackInfos{" + "uuid=" + getUuid() + ", folderName=" + folderName + ", version=" + getVersion()
                + ", sizeInBytes=" + sizeInBytes + ", nightModeAvailable=" + nightModeAvailable + '}';
    }
}
