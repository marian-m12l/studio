/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { DiagramModel } from '@projectstorm/react-diagrams';

import StageNodeModel from "./StageNodeModel";
import CoverNodeModel from "./CoverNodeModel";


class PackDiagramModel extends DiagramModel {

    constructor(title = 'Pack title', version = 1, description = '', nightModeAvailable = false, thumbnail = '') {
        super({
            type: 'pack'
        });
        this.title = title;
        this.version = version;
        this.description = description;
        this.nightModeAvailable = nightModeAvailable;
        this.thumbnail = thumbnail;
    }

    getEntryPoint() {
        return this.getNodes()
            .filter(node => (node instanceof StageNodeModel || node instanceof CoverNodeModel) && node.isSquareOne())[0];
    }

    deserialize(event) {
        super.deserialize(event);
        this.title = event.data.title;
        this.version = event.data.version;
        this.description = event.data.description;
        this.nightModeAvailable = event.data.nightModeAvailable;
        this.thumbnail = event.data.thumbnail;
    }

    serialize() {
        return {
            ...super.serialize(),
            title: this.title,
            version: this.version,
            description: this.description,
            nightModeAvailable: this.nightModeAvailable,
            thumbnail: this.thumbnail
        };
    }

}

export default PackDiagramModel;
