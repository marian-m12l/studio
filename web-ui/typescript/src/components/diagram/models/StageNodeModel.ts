/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { NodeModel } from '@projectstorm/react-diagrams';
import uuidv4 from 'uuid/v4';

import Stage from "./core/Stage";
import StagePortModel from "./StagePortModel";


class StageNodeModel extends NodeModel {

    constructor(options = {}) {
        super({
            ...options,
            type: options.type || 'stage'
        });
        this.uuid = options.uuid || uuidv4();
        this.squareOne = false;
        this.stage = new Stage(options.name || 'Stage title');

        this.fromPort = this.addPort(this.createIncomingPort("from"));
    }

    createIncomingPort(name) {
        return new StagePortModel(name, true);
    }

    createOutgoingPort(name, isHome=false) {
        return new StagePortModel(name, false, isHome);
    }

    getUuid() {
        return this.uuid;
    }

    renewUuid() {
        this.uuid = uuidv4();
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
        if (squareOne) {
            // Remove any attached link
            Object.values(this.fromPort.getLinks())
                .map(link => link.remove());
            // Remove 'from' port
            this.removePort(this.fromPort);
            this.fromPort = null;
        } else {
            // Create 'from' port
            this.fromPort = this.addPort(this.createIncomingPort("from"));
        }
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
            this.okPort = this.addPort(this.createOutgoingPort("ok"));
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
            this.homePort = this.addPort(this.createOutgoingPort("home", true));
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
            this.okPort = this.addPort(this.createOutgoingPort("ok"));
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
            let okTargetPort = okLinks[0].getForwardTargetPort();
            let okTargetNode = okTargetPort.getParent();
            return okTargetNode.onEnter(okTargetPort, diagram);
        }
    }

    onHome(diagram) {
        let homeLinks = Object.values(this.homePort.getLinks());
        if (homeLinks.length !== 1) {
            // Back to main (pack selection) stage node
            let mainNode = diagram.getEntryPoint();
            return [
                mainNode,
                {
                    node: null,
                    index: null
                }
            ];
        } else {
            let homeTargetPort = homeLinks[0].getForwardTargetPort();
            let homeTargetNode = homeTargetPort.getParent();
            return homeTargetNode.onEnter(homeTargetPort, diagram);
        }
    }

    doClone(lookupTable = {}, clone) {
        super.doClone(lookupTable, clone);
        clone.uuid = uuidv4();
        clone.squareOne = false;    // Cannot duplicate a square one node
        clone.stage = this.stage.clone();
        if (this.fromPort) {
            clone.fromPort = this.fromPort.clone(lookupTable);
        }
        if (this.okPort) {
            clone.okPort = this.okPort.clone(lookupTable);
        }
        if (this.homePort) {
            clone.homePort = this.homePort.clone(lookupTable);
        }
    }

    deserialize(event) {
        super.deserialize(event);
        this.uuid = event.data.uuid;
        this.squareOne = event.data.squareOne;
        this.stage = (new Stage()).deserialize(event.data.stage);
        this.fromPort = event.data.fromPort ? this.getPortFromID(event.data.fromPort) : null;
        this.okPort = event.data.okPort ? this.getPortFromID(event.data.okPort) : null;
        this.homePort = event.data.homePort ? this.getPortFromID(event.data.homePort) : null;
    }

    serialize() {
        return {
            ...super.serialize(),
            uuid: this.uuid,
            squareOne: this.squareOne,
            stage: this.stage.serialize(),
            fromPort: this.fromPort ? this.fromPort.getID() : null,
            okPort: this.okPort ? this.okPort.getID() : null,
            homePort: this.homePort ? this.homePort.getID() : null
        };
    }

}

export default StageNodeModel;
