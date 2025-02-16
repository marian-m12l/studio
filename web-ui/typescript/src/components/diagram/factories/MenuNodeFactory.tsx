/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { AbstractReactFactory } from '@projectstorm/react-canvas-core';

import MenuNodeModel from '../models/MenuNodeModel';
import MenuNodeWidget from '../widgets/MenuNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class MenuNodeFactory extends AbstractReactFactory {

    constructor(updateCanvas) {
        super("menu");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(event) {
        return <MenuNodeWidget diagramEngine={this.engine}
                               node={event.model}
                               selected={event.model.isSelected() || false}
                               updateCanvas={this.updateCanvas} />;
    }

    generateModel(event) {
        return new MenuNodeModel();
    }

}

export default MenuNodeFactory;
