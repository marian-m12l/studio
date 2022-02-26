/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

public class Transition {

    private ActionNode actionNode;
    private short optionIndex;

    public Transition(ActionNode actionNode, short optionIndex) {
        this.actionNode = actionNode;
        this.optionIndex = optionIndex;
    }

    public ActionNode getActionNode() {
        return actionNode;
    }

    public void setActionNode(ActionNode actionNode) {
        this.actionNode = actionNode;
    }

    public short getOptionIndex() {
        return optionIndex;
    }

    public void setOptionIndex(short optionIndex) {
        this.optionIndex = optionIndex;
    }
}
