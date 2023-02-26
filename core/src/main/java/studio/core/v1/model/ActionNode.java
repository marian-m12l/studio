/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIdentityReference;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "options")
public class ActionNode extends Node {

    @JsonIdentityReference(alwaysAsId = true)
    private List<StageNode> options;

    public ActionNode(UUID uuid, EnrichedNodeMetadata enriched, List<StageNode> options) {
        super(uuid, enriched);
        this.options = options;
    }
}
