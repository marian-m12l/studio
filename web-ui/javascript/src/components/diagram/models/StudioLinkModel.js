/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { DefaultLinkModel } from '@projectstorm/react-diagrams-defaults';


class StudioLinkModel extends DefaultLinkModel {

    constructor(inversed) {
        super({
            type: 'studio'
        });
        this.inversed = inversed;
    }

    deserialize(event) {
        super.deserialize(event);
        this.inversed = event.data.inversed;
    }

    serialize() {
        return {
            ...super.serialize(),
            inversed: this.inversed
        };
    }

}

export default StudioLinkModel;
