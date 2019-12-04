/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import {LinkLayerFactory} from "@projectstorm/react-diagrams";

import StudioLinkLayerWidget from "../widgets/StudioLinkLayerWidget";


// Factory is in charge of creating a widget from a given model, and of initializing models
class StudioLinkLayerFactory extends LinkLayerFactory {

    constructor() {
        super('studio-link-layer');
    }

    generateReactWidget(event) {
        return <StudioLinkLayerWidget layer={event.model}
                                      engine={this.engine} />;
    }

}

export default StudioLinkLayerFactory;
