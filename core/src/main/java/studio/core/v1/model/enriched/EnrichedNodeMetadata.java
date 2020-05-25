/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.enriched;

public class EnrichedNodeMetadata {

    private String name;
    private EnrichedNodeType type;
    private String groupId;
    private EnrichedNodePosition position;

    public EnrichedNodeMetadata() {
    }

    public EnrichedNodeMetadata(String name, EnrichedNodeType type, String groupId, EnrichedNodePosition position) {
        this.name = name;
        this.type = type;
        this.groupId = groupId;
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EnrichedNodeType getType() {
        return type;
    }

    public void setType(EnrichedNodeType type) {
        this.type = type;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public EnrichedNodePosition getPosition() {
        return position;
    }

    public void setPosition(EnrichedNodePosition position) {
        this.position = position;
    }
}
