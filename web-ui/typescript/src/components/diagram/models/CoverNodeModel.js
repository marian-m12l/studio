/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import StageNodeModel from "./StageNodeModel";


class CoverNodeModel extends StageNodeModel {

    constructor(options = {}) {
        super({
            ...options,
            type: 'cover',
            name: options.name || 'Cover title'
        });
        this.setSquareOne(true);
        this.setControl('wheel', true);
        this.setControl('ok', true);
    }

}

export default CoverNodeModel;
