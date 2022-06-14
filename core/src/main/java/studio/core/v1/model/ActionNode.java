/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;

@Data
@EqualsAndHashCode(callSuper = true, exclude = "options")
public class ActionNode extends Node {

    private List<StageNode> options;

    public ActionNode(EnrichedNodeMetadata enriched, List<StageNode> options) {
        super(enriched);
        this.options = options;
    }
}
