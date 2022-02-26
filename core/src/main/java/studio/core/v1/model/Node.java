/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import studio.core.v1.model.enriched.EnrichedNodeMetadata;

public abstract class Node {

    private final EnrichedNodeMetadata enriched;

    protected Node(EnrichedNodeMetadata enriched) {
        this.enriched = enriched;
    }

    public EnrichedNodeMetadata getEnriched() {
        return enriched;
    }
}
