/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as SRD from 'storm-react-diagrams'


class ActionNodeModel extends SRD.NodeModel {

    constructor(name = 'Action title') {
        super('action');
        this.name = name;
        // Available options
        this.optionsIn = [];
        this.optionsOut = [];
    }

    addOption = () => {
        let index = this.optionsOut.length;
        this.optionsIn[index] = this.addPort(new SRD.DefaultPortModel(true, SRD.Toolkit.UID(), "Option #"+(index+1)));
        this.optionsOut[index] = this.addPort(new SRD.DefaultPortModel(false, SRD.Toolkit.UID(), "Option #"+(index+1)));
        return {
            in: this.optionsIn[index],
            out: this.optionsOut[index]
        };
    };

    removeOption = () => {
        if (this.optionsIn.length > 0) {
            // Remove ports from list
            let optionInPort = this.optionsIn.pop();
            let optionOutPort = this.optionsOut.pop();
            // Remove any attached link
            Object.values(optionInPort.getLinks())
                .map(link => link.remove());
            Object.values(optionOutPort.getLinks())
                .map(link => link.remove());
            // Remove actual ports
            this.removePort(optionInPort);
            this.removePort(optionOutPort);
        }
    };

}

export default ActionNodeModel;
