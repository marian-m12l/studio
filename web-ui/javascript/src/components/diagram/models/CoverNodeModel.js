/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import StageNodeModel from "./StageNodeModel";


class CoverNodeModel extends StageNodeModel {

    constructor(name = 'Cover title', uuid) {
        super(name, uuid);
        this.type = 'cover';
        this.setSquareOne(true);
        this.setControl('wheel', true);
        this.setControl('ok', true);
        // Remove 'from' port
        this.removePort(this.fromPort);
        this.fromPort = null;
    }

}

export default CoverNodeModel;
