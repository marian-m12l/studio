/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { AbstractReactFactory } from '@projectstorm/react-canvas-core';

import StageNodeModel from '../models/StageNodeModel';
import StageNodeWidget from '../widgets/StageNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class StageNodeFactory extends AbstractReactFactory {

    constructor(updateCanvas) {
        super("stage");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(event) {
        return <StageNodeWidget diagramEngine={this.engine}
                                node={event.model}
                                selected={event.model.isSelected() || false}
                                updateCanvas={this.updateCanvas} />;
    }

    generateModel(event) {
        return new StageNodeModel();
    }

}

export default StageNodeFactory;
