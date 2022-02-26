/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import studio.core.v1.model.enriched.EnrichedPackMetadata;

import java.util.List;

public class StoryPack {

    private String uuid;
    private boolean factoryDisabled;
    private short version;
    private List<StageNode> stageNodes;
    private EnrichedPackMetadata enriched;
    private boolean nightModeAvailable = false;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean isFactoryDisabled() {
        return factoryDisabled;
    }

    public void setFactoryDisabled(boolean factoryDisabled) {
        this.factoryDisabled = factoryDisabled;
    }

    public short getVersion() {
        return version;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public List<StageNode> getStageNodes() {
        return stageNodes;
    }

    public void setStageNodes(List<StageNode> stageNodes) {
        this.stageNodes = stageNodes;
    }

    public EnrichedPackMetadata getEnriched() {
        return enriched;
    }

    public void setEnriched(EnrichedPackMetadata enriched) {
        this.enriched = enriched;
    }

    public boolean isNightModeAvailable() {
        return nightModeAvailable;
    }

    public void setNightModeAvailable(boolean nightModeAvailable) {
        this.nightModeAvailable = nightModeAvailable;
    }
}
