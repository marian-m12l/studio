/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { AbstractReactFactory } from '@projectstorm/react-canvas-core';

import ActionNodeModel from '../models/ActionNodeModel';
import ActionNodeWidget from '../widgets/ActionNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class ActionNodeFactory extends AbstractReactFactory {

    constructor(updateCanvas) {
        super("action");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(event) {
        return <ActionNodeWidget diagramEngine={this.engine}
                                 node={event.model}
                                 selected={event.model.isSelected() || false}
                                 updateCanvas={this.updateCanvas} />;
    }

    generateModel(event) {
        return new ActionNodeModel();
    }

}

export default ActionNodeFactory;
