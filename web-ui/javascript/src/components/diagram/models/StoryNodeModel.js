/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import StageNodeModel from "./StageNodeModel";


class StoryNodeModel extends StageNodeModel {

    constructor(name = 'Story title', uuid) {
        super(name, uuid);
        this.type = 'story';
        this.setControl('home', true);
        this.setControl('pause', true);
        this.setControl('autoplay', true);
        // Remove 'ok' and 'home' ports
        this.removePort(this.okPort);
        this.removePort(this.homePort);
        this.okPort = null;
        this.homePort = null;
    }

    onOk(diagram) {
        // TODO Support override of 'ok' port
        return this.goToFirstUsefulNode(diagram);
    }

    onHome(diagram) {
        return this.goToFirstUsefulNode(diagram);
    }

    goToFirstUsefulNode(diagram) {
        // The first node following pack selection (cover) node
        let coverNode = Object.values(diagram.nodes)
            .filter(node => node.squareOne)[0];
        return coverNode.onOk(diagram);
    }

}

export default StoryNodeModel;
