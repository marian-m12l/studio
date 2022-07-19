/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.core.v1.model.enriched.EnrichedPackMetadata;

@Getter
@Setter
@NoArgsConstructor
public class StoryPack {

    private String format = "v1";
    private String uuid;

    @JsonUnwrapped
    private EnrichedPackMetadata enriched;

    private boolean factoryDisabled;
    private short version;
    private boolean nightModeAvailable;

    private List<StageNode> stageNodes;
    private List<ActionNode> actionNodes;
}
