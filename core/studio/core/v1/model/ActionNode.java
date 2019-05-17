/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import java.util.List;

public class ActionNode implements Node {

    private List<StageNode> options;

    public ActionNode() {
    }

    public ActionNode(List<StageNode> options) {
        this.options = options;
    }

    public List<StageNode> getOptions() {
        return options;
    }

    public void setOptions(List<StageNode> options) {
        this.options = options;
    }
}
