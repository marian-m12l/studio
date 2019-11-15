/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as SRD from 'storm-react-diagrams';
import uuidv4 from 'uuid/v4';

import Stage from "./core/Stage";


class StageNodeModel extends SRD.NodeModel {

    constructor(name = 'Stage title', uuid) {
        super('stage');
        this.uuid = uuid || uuidv4();
        this.squareOne = false;
        this.stage = new Stage(name);

        this.fromPort = this.addPort(new SRD.DefaultPortModel(true, SRD.Toolkit.UID(), "from"));
    }

    getUuid() {
        return this.uuid;
    }

    getName() {
        return this.stage.name;
    }

    setName(name) {
        this.stage.name = name;
    }

    isSquareOne() {
        return this.squareOne;
    }

    setSquareOne(squareOne) {
        this.squareOne = squareOne;
        // TODO Enable/disable 'from' port ?
    }

    getImage() {
        return this.stage.image;
    }

    setImage(image) {
        this.stage.image = image;
    }

    getAudio() {
        return this.stage.audio;
    }

    setAudio(audio) {
        this.stage.audio = audio;
    }

    getControls() {
        return this.stage.controls;
    }

    toggleControl(control) {
        this.setControl(control, !this.stage.controls[control]);
    }

    setControl(control, value) {
        if (control === 'ok') {
            this.setOk(value);
        } else if (control === 'home') {
            this.setHome(value);
        } else if (control === 'autoplay') {
            this.setAutoplay(value);
        } else {
            this.stage.controls[control] = value;
        }
    }

    setOk(ok) {
        this.stage.controls.ok = ok;
        if (ok && this.okPort == null) {
            this.okPort = this.addPort(new SRD.DefaultPortModel(false, SRD.Toolkit.UID(), "ok"));
        } else if (!ok && !this.stage.controls.autoplay && this.okPort != null) {
            // Remove any attached link
            Object.values(this.okPort.getLinks())
                .map(link => link.remove());
            // Remove port
            this.removePort(this.okPort);
            this.okPort = null;
        }
    }

    setHome(home) {
        this.stage.controls.home = home;
        if (home && this.homePort == null) {
            this.homePort = this.addPort(new SRD.DefaultPortModel(false, SRD.Toolkit.UID(), "home"));
        } else if (!home && this.homePort != null) {
            // Remove any attached link
            Object.values(this.homePort.getLinks())
                .map(link => link.remove());
            // Remove port
            this.removePort(this.homePort);
            this.homePort = null;
        }
    }

    setAutoplay(autoplay) {
        this.stage.controls.autoplay = autoplay;
        if (autoplay && this.okPort == null) {
            this.okPort = this.addPort(new SRD.DefaultPortModel(false, SRD.Toolkit.UID(), "ok"));
        } else if (!autoplay && !this.stage.controls.ok && this.okPort != null) {
            // Remove any attached link
            Object.values(this.okPort.getLinks())
                .map(link => link.remove());
            // Remove port
            this.removePort(this.okPort);
            this.okPort = null;
        }
    }

    onEnter(port, diagram) {
        return [
            this,
            {
                node: null,
                index: null
            }
        ]
    }

    onOk(diagram) {
        let okLinks = Object.values(this.okPort.getLinks());
        if (okLinks.length !== 1) {
            return [];
        } else {
            let okTargetPort = okLinks[0].getTargetPort();
            let okTargetNode = okTargetPort.getParent();
            return okTargetNode.onEnter(okTargetPort, diagram);
        }
    }

    onHome(diagram) {
        let homeLinks = Object.values(this.homePort.getLinks());
        if (homeLinks.length !== 1) {
            // Back to main (pack selection) stage node
            let mainNode = Object.values(diagram.nodes)
                .filter(node => node.squareOne)[0];
            return [
                mainNode,
                {
                    node: null,
                    index: null
                }
            ];
        } else {
            let homeTargetPort = homeLinks[0].getTargetPort();
            let homeTargetActionNode = homeTargetPort.getParent();
            let targetIndex = (homeTargetPort === homeTargetActionNode.randomOptionIn) ? Math.floor(Math.random() * homeTargetActionNode.optionsOut.length) : homeTargetActionNode.optionsIn.indexOf(homeTargetPort);
            let optionLinks = Object.values(homeTargetActionNode.optionsOut[targetIndex].getLinks());
            if (optionLinks.length !== 1) {
                return [];
            } else {
                let nextNode = optionLinks[0].getTargetPort().getParent();

                return [
                    nextNode,
                    {
                        node: homeTargetActionNode,
                        index: targetIndex
                    }
                ];
            }
        }
    }

}

export default StageNodeModel;
