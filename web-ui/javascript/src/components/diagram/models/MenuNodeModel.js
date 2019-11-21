/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as SRD from 'storm-react-diagrams';
import uuidv4 from "uuid/v4";

import Stage from "./core/Stage";
import ActionPortModel from "./ActionPortModel";
import StagePortModel from "./StagePortModel";


class MenuNodeModel extends SRD.NodeModel {

    constructor(name = 'Menu title', uuid) {
        super('menu');
        this.uuid = uuid || uuidv4();
        this.name = name;
        this.fromPort = this.addPort(new ActionPortModel(true, SRD.Toolkit.UID(), "from"));
        // Question stage
        this.questionStage = new Stage(name+".questionstage");
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

    getName() {
        return this.name;
    }

    setName(name) {
        this.name = name;
        this.questionStage.name = name+".questionstage";
    }

    addOption = () => {
        let index = this.optionsStages.length;

        this.optionsStages[index] = new Stage(`optionsstage.${index}`);
        this.optionsStages[index].controls['wheel'] = true;
        this.optionsStages[index].controls['ok'] = true;
        this.optionsStages[index].controls['home'] = true;
        this.optionsOut[index] = this.addPort(new StagePortModel(false, SRD.Toolkit.UID(), "Option #"+(index+1)));
        return this.optionsOut[index];
    };

    removeOption = () => {
        // Keep at least one option
        if (this.optionsStages.length > 1) {
            // Remove stages and ports from list
            let optionStage = this.optionsStages.pop();
            let optionOutPort = this.optionsOut.pop();
            // Remove any attached link
            Object.values(optionOutPort.getLinks())
                .map(link => link.remove());
            // Remove actual ports
            this.removePort(optionOutPort);
            // Make sure default option is consistent with the number of remaining options
            this.defaultOption = Math.min(this.defaultOption, this.optionsStages.length-1);
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
        let targetIndex = (this.defaultOption === -1) ? Math.floor(Math.random() * this.optionsStages.length) : this.defaultOption;
        let that = this;
        return [
            {
                stage: this.questionStage,
                getImage: () => this.questionStage.image,
                getAudio: () => this.questionStage.audio,
                getControls: () => this.questionStage.controls,
                onOk: (d) => {
                    // Move to default option
                    return [
                        that.wrapOptionStage(that.optionsStages[targetIndex], targetIndex, d),
                        {
                            node: that,
                            index: targetIndex
                        }
                    ]
                }
            },
            {
                node: null,
                index: null
            }
        ];
    }

    onWheelLeft(index, diagram) {
        let nextIndex = index === 0 ? (this.optionsStages.length - 1) : (index - 1);
        let nextOption = this.optionsStages[nextIndex];

        return [
            this.wrapOptionStage(nextOption, nextIndex, diagram),
            {
                node: this,
                index: nextIndex
            }
        ];
    }

    onWheelRight(index, diagram) {
        let nextIndex = (index + 1) % this.optionsOut.length;
        let nextOption = this.optionsStages[nextIndex];

        return [
            this.wrapOptionStage(nextOption, nextIndex, diagram),
            {
                node: this,
                index: nextIndex
            }
        ];
    }

    wrapOptionStage(stage, index, diagram) {
        let that = this;
        return {
            stage: stage,
            getImage: () => stage.image,
            getAudio: () => stage.audio,
            getControls: () => stage.controls,
            onOk: (d) => {
                // Transition to selected option
                let optionOutLinks = Object.values(that.optionsOut[index].getLinks());
                if (optionOutLinks.length !== 1) {
                    return [];
                } else {
                    let optionOutTargetPort = optionOutLinks[0].getTargetPort();
                    let optionOutTargetNode = optionOutTargetPort.getParent();
                    return optionOutTargetNode.onEnter(optionOutTargetPort, d);
                }
            },
            onHome: (d) => {
                // Go back to previous node, or to pack cover
                let fromLinks = Object.values(that.fromPort.getLinks());
                if (fromLinks.length !== 1) {
                    let coverNode = Object.values(d.nodes)
                        .filter(node => node.squareOne)[0];
                    return [
                        coverNode,
                        {
                            node: null,
                            index: null
                        }
                    ]
                } else {
                    let previousNode = fromLinks[0].getSourcePort().getParent();
                    return previousNode.onEnter(null, d);   // TODO should not be an action node ! add link constraints ?
                }
            }
        };
    }

}

export default MenuNodeModel;
