/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as SRD from 'storm-react-diagrams'


class PackDiagramModel extends SRD.DiagramModel {

    constructor(title = 'Pack title', version = 1, description = '', thumbnail = '') {
        super('pack');
        this.title = title;
        this.version = version;
        this.description = description;
        this.thumbnail = thumbnail;
    }

}

export default PackDiagramModel;
