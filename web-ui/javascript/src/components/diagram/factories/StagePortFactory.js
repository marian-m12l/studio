/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { AbstractModelFactory } from '@projectstorm/react-canvas-core';

import StagePortModel from "../models/StagePortModel";


// Factory is in charge of initializing models
class StagePortFactory extends AbstractModelFactory {

    constructor() {
        super('stage-port');
    }

    generateModel(event) {
        return new StagePortModel();
    }

}

export default StagePortFactory;
