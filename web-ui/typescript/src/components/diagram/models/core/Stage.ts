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

    clone() {
        let clone = Object.assign(Object.create(Object.getPrototypeOf(this)), this);
        clone.controls = Object.assign({}, this.controls);
        return clone;
    };

    deserialize(data) {
        this.name = data.name;
        this.image = data.image;
        this.audio = data.audio;
        this.controls = data.controls;
    }

    serialize() {
        return {
            name: this.name,
            image: this.image,
            audio: this.audio,
            controls: this.controls
        };
    }

}

export default Stage;
