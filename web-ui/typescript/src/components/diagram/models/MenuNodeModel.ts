/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { NodeModel } from '@projectstorm/react-diagrams';
import {v4 as uuidv4} from "uuid";

import Stage from "./core/Stage";
import ActionPortModel from "./ActionPortModel";
import StagePortModel from "./StagePortModel";

interface Options{
    uuid?: string;
    name?: string;
}

export default class MenuNodeModel extends NodeModel {
    uuid: string;
    name: string;
    questionStage: Stage;
    optionsStages: never[];
    optionsOut: never[];
    defaultOption: number;
    fromPort: any;

    constructor(options:Options = {}) {
        super({
            ...options,
            type: 'menu'
        });
        this.uuid = options.uuid || uuidv4();
        this.name = options.name || 'Menu title';
        this.fromPort = this.addPort(new ActionPortModel("from", true));
        // Question stage
        this.questionStage = new Stage(this.name+".questionstage");
        this.questionStage.controls['autoplay'] = true;
        // Option stages
        this.optionsStages = [];
        this.optionsOut = [];
        // Default option
        this.defaultOption = 0;
    }

    getUuid() {
        return this.uuid;
    }

    renewUuid() {
        this.uuid = uuidv4();
    }

    getName() {
        return this.name;
    }

    setName(name :string) {
        this.name = name;
        this.questionStage.name = name+".questionstage";
    }

    addOption() {
        const index = this.optionsStages.length;

        this.optionsStages[index] = new Stage(`Option #${index+1}`);
        this.optionsStages[index].controls['wheel'] = true;
        this.optionsStages[index].controls['ok'] = true;
        this.optionsStages[index].controls['home'] = true;
        this.optionsOut[index] = this.addPort(new StagePortModel("Option #"+(index+1), false));
        return this.optionsOut[index];
    };

    removeOption(idx=-1) {
        // Keep at least one option
        if (this.optionsStages.length > 1) {
            // Remove stages and ports from list
            this.optionsStages.splice(idx, 1);
            const optionOutPort = this.optionsOut.splice(idx, 1)[0];
            // Remove any attached link
            Object.values(optionOutPort.getLinks())
                .map(link => link.remove());
            // Remove actual ports
            this.removePort(optionOutPort);
            // Make sure default option is updated and consistent with the number of remaining options
            if (idx >= 0 && this.defaultOption > idx) {
                this.defaultOption--;
            } else {
                this.defaultOption = Math.min(this.defaultOption, this.optionsStages.length - 1);
            }
        }
    };

    getQuestionAudio() {
        return this.questionStage.audio;
    }

    setQuestionAudio(audio) {
        this.questionStage.audio = audio;
    }

    getOptionName(index) {
        return this.optionsStages[index].name;
    }

    setOptionName(index, name) {
        this.optionsStages[index].name = name;
    }

    getOptionImage(index) {
        return this.optionsStages[index].image;
    }

    setOptionImage(index, image) {
        this.optionsStages[index].image = image;
    }

    getOptionAudio(index) {
        return this.optionsStages[index].audio;
    }

    setOptionAudio(index, audio) {
        this.optionsStages[index].audio = audio;
    }

    getDefaultOption() {
        return this.defaultOption;
    }

    setDefaultOption(index) {
        this.defaultOption = index;
    }


    onEnter(port, diagram) {
        const targetIndex = (this.defaultOption === -1) ? Math.floor(Math.random() * this.optionsStages.length) : this.defaultOption;
        return [
            {
                stage: this.questionStage,
                getImage: () => this.questionStage.image,
                getAudio: () => this.questionStage.audio,
                getControls: () => this.questionStage.controls,
                onOk: (d) => {
                    // Move to default option
                    return [
                        this.wrapOptionStage(this.optionsStages[targetIndex], targetIndex, d),
                        {
                            node: this,
                            index: targetIndex
                        }
                    ]
                },
                parentNode: this
            },
            {
                node: null,
                index: null
            }
        ];
    }

    onWheelLeft(index, diagram) {
        const nextIndex = index === 0 ? (this.optionsStages.length - 1) : (index - 1);
        const nextOption = this.optionsStages[nextIndex];

        return [
            this.wrapOptionStage(nextOption, nextIndex, diagram),
            {
                node: this,
                index: nextIndex
            }
        ];
    }

    onWheelRight(index, diagram) {
        const nextIndex = (index + 1) % this.optionsOut.length;
        const nextOption = this.optionsStages[nextIndex];

        return [
            this.wrapOptionStage(nextOption, nextIndex, diagram),
            {
                node: this,
                index: nextIndex
            }
        ];
    }

    wrapOptionStage(stage, index, diagram) {
        const that = this;
        return {
            stage: stage,
            getImage: () => stage.image,
            getAudio: () => stage.audio,
            getControls: () => stage.controls,
            onOk: (d) => {
                // Transition to selected option
                const optionOutLinks = Object.values(that.optionsOut[index].getLinks());
                if (optionOutLinks.length !== 1) {
                    return [];
                } else {
                    const optionOutTargetPort = optionOutLinks[0].getForwardTargetPort();
                    const optionOutTargetNode = optionOutTargetPort.getParent();
                    return optionOutTargetNode.onEnter(optionOutTargetPort, d);
                }
            },
            onHome: (d) => {
                // Go back to previous node, or to pack cover
                const fromLinks = Object.values(that.fromPort.getLinks());
                if (fromLinks.length !== 1) {
                    const coverNode = d.getEntryPoint();
                    return [
                        coverNode,
                        {
                            node: null,
                            index: null
                        }
                    ]
                } else {
                    const previousNode = fromLinks[0].getForwardSourcePort().getParent();
                    return previousNode.onEnter(null, d);
                }
            },
            parentNode: that
        };
    }

    doClone(lookupTable = {}, clone) {
        super.doClone(lookupTable, clone);
        clone.uuid = uuidv4();
        clone.fromPort = this.fromPort.clone(lookupTable);
        clone.questionStage = this.questionStage.clone();
        clone.optionsStages = this.optionsStages.map(optionStage => optionStage.clone());
        clone.optionsOut = this.optionsOut.map(optionOutPort => optionOutPort.clone(lookupTable));
    }

    deserialize(event) {
        super.deserialize(event);
        this.uuid = event.data.uuid;
        this.name = event.data.name;
        this.fromPort = this.getPortFromID(event.data.fromPort);
        this.questionStage = (new Stage()).deserialize(event.data.questionStage);
        this.optionsStages = event.data.optionsStages.map(os => (new Stage()).deserialize(os));
        this.optionsOut = event.data.optionsOut.map(id => this.getPortFromID(id));
        this.defaultOption = event.data.defaultOption;
    }

    serialize() {
        return {
            ...super.serialize(),
            uuid: this.uuid,
            fromPort: this.fromPort.getID(),
            questionStage: this.questionStage.serialize(),
            optionsStages: this.optionsStages.map(os => os.serialize()),
            optionsOut: this.optionsOut.map(port => port.getID()),
            defaultOption: this.defaultOption
        };
    }

}