/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import java.util.List;

public class StoryPack {

    private boolean factoryDisabled;
    private short version;
    private List<StageNode> stageNodes;

    public StoryPack() {
    }

    public StoryPack(boolean factoryDisabled, short version, List<StageNode> stageNodes) {
        this.factoryDisabled = factoryDisabled;
        this.version = version;
        this.stageNodes = stageNodes;
    }

    public String getUuid() {
        return this.stageNodes.get(0).getUuid();
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
}
