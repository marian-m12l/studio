/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import studio.core.v1.model.enriched.EnrichedNodeMetadata;

import java.util.List;

public class ActionNode extends Node {

    private List<StageNode> options;

    public ActionNode(EnrichedNodeMetadata enriched, List<StageNode> options) {
        super(enriched);
        this.options = options;
    }

    public List<StageNode> getOptions() {
        return options;
    }

    public void setOptions(List<StageNode> options) {
        this.options = options;
    }
}
