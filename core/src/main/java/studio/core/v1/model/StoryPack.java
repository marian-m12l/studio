/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import studio.core.v1.model.enriched.EnrichedPackMetadata;

import java.util.List;

import lombok.Data;

@Data
public class StoryPack {

    private String uuid;
    private boolean factoryDisabled;
    private short version;
    private List<StageNode> stageNodes;
    private EnrichedPackMetadata enriched;
    private boolean nightModeAvailable = false;
}
