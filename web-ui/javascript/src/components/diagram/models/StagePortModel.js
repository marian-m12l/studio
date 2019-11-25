/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as SRD from 'storm-react-diagrams'

import ActionPortModel from "./ActionPortModel";


class StagePortModel extends SRD.DefaultPortModel {

    canLinkToPort(port) {
        return (
            port instanceof ActionPortModel             // A stage port must be linked to an action port
            && this.in !== port.in                      // Make sure an outgoing port is linked to an incoming port
            && this.getParent() !== port.getParent()    // The source and target nodes of a link should not be on the same node
            && (                                        // Outgoing ports have a maximum of one link
                (this.in === true && Object.keys(port.links).length <= 1)
                || (this.in === false && Object.keys(this.links).length <= 1)
            )
        );
    }

}

export default StagePortModel;
