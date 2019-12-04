/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { AbstractReactFactory } from '@projectstorm/react-canvas-core';

import CoverNodeModel from '../models/CoverNodeModel';
import CoverNodeWidget from '../widgets/CoverNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class CoverNodeFactory extends AbstractReactFactory {

    constructor(updateCanvas) {
        super("cover");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(event) {
        return <CoverNodeWidget diagramEngine={this.engine}
                                node={event.model}
                                selected={event.model.isSelected() || false}
                                updateCanvas={this.updateCanvas} />;
    }

    generateModel(event) {
        return new CoverNodeModel();
    }

}

export default CoverNodeFactory;
