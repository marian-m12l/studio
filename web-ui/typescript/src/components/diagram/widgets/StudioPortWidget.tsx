/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import { DiagramEngine, PortModel, PortWidget } from '@projectstorm/react-diagrams';

import ActionPortModel from "../models/ActionPortModel";
import StagePortModel from "../models/StagePortModel";


class StudioPortWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            selected: false
        };
    }

    editSelected = (selected) => {
        return () => this.setState({ selected });
    };

    render() {
        let classes = 'custom-port ';
        if (this.props.model instanceof ActionPortModel) {
            classes += 'action-port ';
        } else if (this.props.model instanceof StagePortModel) {
            classes += 'stage-port ';
        }
        if (this.state.selected) {
            classes += 'selected ';
        }
        if (this.props.className) {
            classes += this.props.className
        }
        return (
            <PortWidget
                className={classes}
                port={this.props.model}
                engine={this.props.engine}>
                <div
                    className="pad"
                    title={this.props.model.label}
                    onMouseEnter={this.editSelected(true)}
                    onMouseLeave={this.editSelected(false)}
                />
            </PortWidget>
        )
    }

}

StudioPortWidget.propTypes = {
    model: PropTypes.instanceOf(PortModel).isRequired,
    engine: PropTypes.instanceOf(DiagramEngine).isRequired,
    className: PropTypes.string
};

export default StudioPortWidget
