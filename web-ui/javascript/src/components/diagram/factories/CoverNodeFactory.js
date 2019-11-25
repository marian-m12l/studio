/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import * as SRD from 'storm-react-diagrams';

import CoverNodeModel from '../models/CoverNodeModel';
import CoverNodeWidget from '../widgets/CoverNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class CoverNodeFactory extends SRD.AbstractNodeFactory {

    constructor(updateCanvas) {
        super("cover");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(diagramEngine, node) {
        return <CoverNodeWidget diagramEngine={diagramEngine} node={node} updateCanvas={this.updateCanvas} />;
    }

    getNewInstance(initialConfig) {
        return new CoverNodeModel();
    }

}

export default CoverNodeFactory;
