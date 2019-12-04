/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import {DefaultLinkFactory} from "@projectstorm/react-diagrams-defaults";

import StudioLinkModel from "../models/StudioLinkModel";
import StudioLinkWidget from "../widgets/StudioLinkWidget";


// Factory is in charge of creating a widget from a given model, and of initializing models
class StudioLinkFactory extends DefaultLinkFactory {

    constructor() {
        super('studio');
    }

    generateReactWidget(event) {
        return <StudioLinkWidget diagramEngine={this.engine}
                                 link={event.model} />;
    }

    generateLinkSegment(model, selected, path) {
        return <path
            className={selected ? 'selected' : ''}
            fill="none"
            pointerEvents="all"
            strokeWidth={model.getOptions().width}
            stroke={selected ? model.getOptions().selectedColor : model.getOptions().color}
            d={path}
        />;
    }

    generateModel() {
        return new StudioLinkModel();
    }

}

export default StudioLinkFactory;
