/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import { DiagramEngine } from '@projectstorm/react-diagrams';

import StudioLinkModel from "../models/StudioLinkModel";


class StudioLinkSegmentWidget extends React.Component {

    render() {
        const Bottom = React.cloneElement(
            this.props.factory.generateLinkSegment(
                this.props.link,
                this.props.selected || this.props.link.isSelected(),
                this.props.path
            ),
            {
                ref: this.props.forwardRef,
                markerStart: (this.props.link.inversed && this.props.firstSegment ? 'url(#arrowhead-start)' : null),
                markerEnd: (!this.props.link.inversed && this.props.lastSegment ? 'url(#arrowhead-end)' : null)
            }
        );

        const Top = React.cloneElement(Bottom, {
            strokeLinecap: 'round',
            onMouseLeave: () => {
                this.props.onSelection(false);
            },
            onMouseEnter: () => {
                this.props.onSelection(true);
            },
            ...this.props.extras,
            ref: null,
            'data-linkid': this.props.link.getID(),
            strokeOpacity: this.props.selected ? 0.1 : 0,
            strokeWidth: 20,
            fill: 'none',
            onContextMenu: (event) => {
                if (!this.props.link.isLocked()) {
                    event.preventDefault();
                    this.props.link.remove();
                }
            },
            markerStart: null,
            markerEnd: null
        });

        return (
            <g>
                {Bottom}
                {Top}
            </g>
        );
    }

}

StudioLinkSegmentWidget.propTypes = {
    path: PropTypes.string.isRequired,
    link: PropTypes.instanceOf(StudioLinkModel).isRequired,
    selected: PropTypes.bool.isRequired,
    forwardRef: PropTypes.any.isRequired,
    factory: PropTypes.object.isRequired,
    diagramEngine: PropTypes.instanceOf(DiagramEngine).isRequired,
    onSelection: PropTypes.func.isRequired,
    extras: PropTypes.object.isRequired,
    firstSegment: PropTypes.bool.isRequired,
    lastSegment: PropTypes.bool.isRequired
};

export default StudioLinkSegmentWidget
