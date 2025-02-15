/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { NodeModel } from '@projectstorm/react-diagrams';

import ActionPortModel from "./ActionPortModel";


class ActionNodeModel extends NodeModel {

    constructor(options = {}) {
        super({
            ...options,
            type: 'action'
        });
        this.name = options.name || 'Action title';
        // Available options
        this.optionsIn = [];
        this.optionsOut = [];
        // Random option
        this.randomOptionIn = this.addPort(new ActionPortModel("Random option", true));
    }

    getName() {
        return this.name;
    }

    setName(name) {
        this.name = name;
    }

    addOption() {
        let index = this.optionsOut.length;
        this.optionsIn[index] = this.addPort(new ActionPortModel("Option #"+(index+1), true));
        this.optionsOut[index] = this.addPort(new ActionPortModel("Option #"+(index+1), false));
        return {
            in: this.optionsIn[index],
            out: this.optionsOut[index]
        };
    };

    removeOption(idx=-1) {
        if (this.optionsIn.length > 1) {
            // Remove ports from list
            let optionInPort = this.optionsIn.splice(idx, 1)[0];
            let optionOutPort = this.optionsOut.splice(idx, 1)[0];
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
            let nextNode = optionLinks[0].getForwardTargetPort().getParent();

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
            let nextChoice = optionLinks[0].getForwardTargetPort().getParent();

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
            let nextChoice = optionLinks[0].getForwardTargetPort().getParent();

            return [
                nextChoice,
                {
                    node: this,
                    index: nextIndex
                }
            ];
        }
    }

    doClone(lookupTable = {}, clone) {
        super.doClone(lookupTable, clone);
        clone.optionsIn = this.optionsIn.map(optionInPort => optionInPort.clone(lookupTable));
        clone.randomOptionIn = this.randomOptionIn.clone(lookupTable);
        clone.optionsOut = this.optionsOut.map(optionOutPort => optionOutPort.clone(lookupTable));
    }

    deserialize(event) {
        super.deserialize(event);
        this.name = event.data.name;
        this.optionsIn = event.data.optionsIn.map(id => this.getPortFromID(id));
        this.optionsOut = event.data.optionsOut.map(id => this.getPortFromID(id));
        this.randomOptionIn = this.getPortFromID(event.data.randomOptionIn);
    }

    serialize() {
        return {
            ...super.serialize(),
            name: this.name,
            optionsIn: this.optionsIn.map(port => port.getID()),
            optionsOut: this.optionsOut.map(port => port.getID()),
            randomOptionIn: this.randomOptionIn.getID()
        };
    }

}

export default ActionNodeModel;
