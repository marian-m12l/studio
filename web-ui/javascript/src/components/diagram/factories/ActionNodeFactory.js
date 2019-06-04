/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import * as SRD from 'storm-react-diagrams';

import ActionNodeModel from '../models/ActionNodeModel';
import ActionNodeWidget from '../widgets/ActionNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class ActionNodeFactory extends SRD.AbstractNodeFactory {

    constructor(updateCanvas) {
        super("action");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(diagramEngine, node) {
        return <ActionNodeWidget diagramEngine={diagramEngine} node={node} updateCanvas={this.updateCanvas} />;
    }

    getNewInstance(initialConfig) {
        return new ActionNodeModel();
    }

}

export default ActionNodeFactory;
