/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import StageNodeModel from "./StageNodeModel";
import ActionPortModel from "./ActionPortModel";


class StoryNodeModel extends StageNodeModel {

    constructor(options = {}) {
        super({
            ...options,
            type: 'story',
            name: options.name || 'Story title'
        });
        this.customOkTransition = false;
        this.customHomeTransition = false;
        this.disableHome = false;
        this.setControl('home', true);
        this.setControl('pause', true);
        this.setControl('autoplay', true);
        // Remove 'ok' and 'home' ports
        this.removePort(this.okPort);
        this.removePort(this.homePort);
        this.okPort = null;
        this.homePort = null;
    }

    // Incoming node is of type 'action'
    createIncomingPort(name) {
        return new ActionPortModel(name, true);
    }

    setCustomOkTransition(isEnabled) {
        this.customOkTransition = isEnabled;
        if (isEnabled) {
            // Creates 'ok' port
            this.setControl('autoplay', true);
        } else if (this.okPort) {
            // Remove any attached link
            Object.values(this.okPort.getLinks())
                .map(link => link.remove());
            // Remove 'ok' port if necessary
            this.removePort(this.okPort);
            this.okPort = null;
        }
    }

    setCustomHomeTransition(isEnabled) {
        this.customHomeTransition = isEnabled;
        if (isEnabled) {
            // Creates 'home' port
            this.setControl('home', true);
        } else if (this.homePort) {
            // Remove any attached link
            Object.values(this.homePort.getLinks())
                .map(link => link.remove());
            // Remove 'home' port if necessary
            this.removePort(this.homePort);
            this.homePort = null;
        }
    }

    setDisableHome(disableHome) {
        this.disableHome = disableHome;
        if (disableHome) {
            // Removes 'home' port and links
            this.setControl('home', false);
            this.setCustomHomeTransition(false);
        }
    }

    onOk(diagram) {
        // 'ok' behaviour may be overridden
        if (this.customOkTransition) {
            return super.onOk(diagram);
        } else {
            return this.goToFirstUsefulNode(diagram);
        }
    }

    onHome(diagram) {
        // 'home' behaviour may be overridden
        if (this.customHomeTransition) {
            return super.onHome(diagram);
        } else {
            return this.goToFirstUsefulNode(diagram);
        }
    }

    goToFirstUsefulNode(diagram) {
        // The first node following pack selection (cover) node
        let coverNode = diagram.getEntryPoint();
        return coverNode.onOk(diagram);
    }

    deserialize(event) {
        super.deserialize(event);
        this.customOkTransition = event.data.customOkTransition;
        this.customHomeTransition = event.data.customHomeTransition;
        this.disableHome = event.data.disableHome;
    }

    serialize() {
        return {
            ...super.serialize(),
            customOkTransition: this.customOkTransition,
            customHomeTransition: this.customHomeTransition,
            disableHome: this.disableHome
        };
    }

}

export default StoryNodeModel;
