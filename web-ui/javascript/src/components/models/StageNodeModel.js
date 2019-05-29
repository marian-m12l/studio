/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as SRD from 'storm-react-diagrams';
import uuidv4 from 'uuid/v4';


class StageNodeModel extends SRD.NodeModel {

    constructor(name = 'Stage title', uuid) {
        super('stage');
        this.uuid = uuid || uuidv4();
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

        this.fromPort = this.addPort(new SRD.DefaultPortModel(true, SRD.Toolkit.UID(), "from"));
    }

    toggleControl(control) {
        this.setControl(control, !this.controls[control]);
    }

    setControl(control, value) {
        if (control === 'ok') {
            this.setOk(value);
        } else if (control === 'home') {
            this.setHome(value);
        } else if (control === 'autoplay') {
            this.setAutoplay(value);
        } else {
            this.controls[control] = value;
        }
    }

    setOk(ok) {
        this.controls.ok = ok;
        if (ok && this.okPort == null) {
            this.okPort = this.addPort(new SRD.DefaultPortModel(false, SRD.Toolkit.UID(), "ok"));
        } else if (!ok && !this.controls.autoplay && this.okPort != null) {
            // Remove any attached link
            Object.values(this.okPort.getLinks())
                .map(link => link.remove());
            // Remove port
            this.removePort(this.okPort);
            this.okPort = null;
        }
    }

    setHome(home) {
        this.controls.home = home;
        if (home && this.homePort == null) {
            this.homePort = this.addPort(new SRD.DefaultPortModel(false, SRD.Toolkit.UID(), "home"));
        } else if (!home && this.homePort != null) {
            // Remove any attached link
            Object.values(this.homePort.getLinks())
                .map(link => link.remove());
            // Remove port
            this.removePort(this.homePort);
            this.homePort = null;
        }
    }

    setAutoplay(autoplay) {
        this.controls.autoplay = autoplay;
        if (autoplay && this.okPort == null) {
            this.okPort = this.addPort(new SRD.DefaultPortModel(false, SRD.Toolkit.UID(), "ok"));
        } else if (!autoplay && !this.controls.ok && this.okPort != null) {
            // Remove any attached link
            Object.values(this.okPort.getLinks())
                .map(link => link.remove());
            // Remove port
            this.removePort(this.okPort);
            this.okPort = null;
        }
    }

}

export default StageNodeModel;
