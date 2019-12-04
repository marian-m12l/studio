/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { AbstractReactFactory } from '@projectstorm/react-canvas-core';

import StoryNodeModel from '../models/StoryNodeModel';
import StoryNodeWidget from '../widgets/StoryNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class StoryNodeFactory extends AbstractReactFactory {

    constructor(updateCanvas) {
        super("story");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(event) {
        return <StoryNodeWidget diagramEngine={this.engine}
                                node={event.model}
                                selected={event.model.isSelected() || false}
                                updateCanvas={this.updateCanvas} />;
    }

    generateModel(event) {
        return new StoryNodeModel();
    }

}

export default StoryNodeFactory;
