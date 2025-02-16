/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import { DiagramEngine, LinkLayerModel, LinkWidget } from '@projectstorm/react-diagrams';


class StudioLinkLayerWidget extends React.Component {

    render() {
        return (
            <>
                <defs>
                    <marker id="arrowhead-end"
                            viewBox="0 0 10 10"
                            refX="10"
                            refY="5"
                            markerUnits="strokeWidth"
                            markerWidth="5"
                            markerHeight="5"
                            orient="auto">
                        <polygon points="0,0 10,5 0,10" className="arrowhead">
                        </polygon>
                    </marker>
                    <marker id="arrowhead-start"
                            viewBox="0 0 10 10"
                            refX="0"
                            refY="5"
                            markerUnits="strokeWidth"
                            markerWidth="5"
                            markerHeight="5"
                            orient="auto">
                        <polygon points="10,0 0,5 10,10" className="arrowhead">
                        </polygon>
                    </marker>
                </defs>
                {//only perform these actions when we have a diagram
                    Object.values(this.props.layer.getLinks()).map(link => {
                        return <LinkWidget key={link.getID()} link={link} diagramEngine={this.props.engine} />;
                    })
                }
            </>
        );
    }

}

StudioLinkLayerWidget.propTypes = {
    layer: PropTypes.instanceOf(LinkLayerModel).isRequired,
    engine: PropTypes.instanceOf(DiagramEngine).isRequired
};

export default StudioLinkLayerWidget
