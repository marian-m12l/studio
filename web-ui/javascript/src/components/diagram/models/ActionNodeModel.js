/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as SRD from 'storm-react-diagrams'

import ActionPortModel from "./ActionPortModel";


class ActionNodeModel extends SRD.NodeModel {

    constructor(name = 'Action title') {
        super('action');
        this.name = name;
        // Available options
        this.optionsIn = [];
        this.optionsOut = [];
        // Random option
        this.randomOptionIn = this.addPort(new ActionPortModel(true, SRD.Toolkit.UID(), "Random option"));
    }

    getUuid() {
        return this.uuid;
    }

    getName() {
        return this.name;
    }

    setName(name) {
        this.name = name;
    }

    addOption = () => {
        let index = this.optionsOut.length;
        this.optionsIn[index] = this.addPort(new ActionPortModel(true, SRD.Toolkit.UID(), "Option #"+(index+1)));
        this.optionsOut[index] = this.addPort(new ActionPortModel(false, SRD.Toolkit.UID(), "Option #"+(index+1)));
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

    onEnter(port, diagram) {
        let targetIndex = (port === this.randomOptionIn) ? Math.floor(Math.random() * this.optionsOut.length) : this.optionsIn.indexOf(port);
        let optionLinks = Object.values(this.optionsOut[targetIndex].getLinks());
        if (optionLinks.length !== 1) {
            return [];
        } else {
            let nextNode = optionLinks[0].getTargetPort().getParent();

            return [
                nextNode,
                {
                    node: this,
                    index: targetIndex
                }
            ];
        }
    }

    onWheelLeft(index, diagram) {
        let nextIndex = index === 0 ? (this.optionsOut.length - 1) : (index - 1);
        let optionLinks = Object.values(this.optionsOut[nextIndex].getLinks());
        if (optionLinks.length !== 1) {
            return [];
        } else {
            let nextChoice = optionLinks[0].getTargetPort().getParent();

            return [
                nextChoice,
                {
                    node: this,
                    index: nextIndex
                }
            ];
        }
    }

    onWheelRight(index, diagram) {
        let nextIndex = (index + 1) % this.optionsOut.length;
        let optionLinks = Object.values(this.optionsOut[nextIndex].getLinks());
        if (optionLinks.length !== 1) {
            return [];
        } else {
            let nextChoice = optionLinks[0].getTargetPort().getParent();

            return [
                nextChoice,
                {
                    node: this,
                    index: nextIndex
                }
            ];
        }
    }

}

export default ActionNodeModel;
