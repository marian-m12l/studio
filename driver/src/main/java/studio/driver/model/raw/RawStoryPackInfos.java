/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.raw;

import studio.driver.model.StoryPackInfos;

public class RawStoryPackInfos extends StoryPackInfos {

    private int startSector;
    private int sizeInSectors;
    private short statsOffset;
    private short samplingRate;

    public int getStartSector() {
        return startSector;
    }

    public void setStartSector(int startSector) {
        this.startSector = startSector;
    }

    public int getSizeInSectors() {
        return sizeInSectors;
    }

    public void setSizeInSectors(int sizeInSectors) {
        this.sizeInSectors = sizeInSectors;
    }

    public short getStatsOffset() {
        return statsOffset;
    }

    public void setStatsOffset(short statsOffset) {
        this.statsOffset = statsOffset;
    }

    public short getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(short samplingRate) {
        this.samplingRate = samplingRate;
    }

    @Override
    public String toString() {
        return "RawStoryPackInfos{" + "uuid=" + getUuid() + ", version=" + getVersion() + ", startSector=" + startSector
                + ", sizeInSectors=" + sizeInSectors + ", statsOffset=" + statsOffset + ", samplingRate=" + samplingRate
                + '}';
    }
}
