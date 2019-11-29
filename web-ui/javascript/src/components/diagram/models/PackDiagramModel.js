/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { DiagramModel } from '@projectstorm/react-diagrams';

import StageNodeModel from "./StageNodeModel";
import CoverNodeModel from "./CoverNodeModel";


class PackDiagramModel extends DiagramModel {

    constructor(title = 'Pack title', version = 1, description = '', thumbnail = '') {
        super({
            type: 'pack'
        });
        this.title = title;
        this.version = version;
        this.description = description;
        this.thumbnail = thumbnail;
    }

    getEntryPoint() {
        return this.getNodes()
            .filter(node => (node instanceof StageNodeModel || node instanceof CoverNodeModel) && node.isSquareOne())[0];
    }

}

export default PackDiagramModel;
