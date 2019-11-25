/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

class Stage {

    constructor(name) {
        this.name = name;
        this.image = null;
        this.audio = null;
        this.controls = {
            wheel: false,
            ok: false,
            home: false,
            pause: false,
            autoplay: false
        };
    }

}

export default Stage;
