/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.raw;

import java.util.UUID;

public class RawStoryPackInfos {

    private UUID uuid;
    private short version;
    private int startSector;
    private int sizeInSectors;
    private short statsOffset;
    private short samplingRate;

    public RawStoryPackInfos() {
    }

    public RawStoryPackInfos(UUID uuid, short version, int startSector, int sizeInSectors, short statsOffset, short samplingRate) {
        this.uuid = uuid;
        this.version = version;
        this.startSector = startSector;
        this.sizeInSectors = sizeInSectors;
        this.statsOffset = statsOffset;
        this.samplingRate = samplingRate;
    }

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
        return "RawStoryPackInfos{" +
                "uuid=" + uuid +
                ", version=" + version +
                ", startSector=" + startSector +
                ", sizeInSectors=" + sizeInSectors +
                ", statsOffset=" + statsOffset +
                ", samplingRate=" + samplingRate +
                '}';
    }
}
