/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import * as SRD from 'storm-react-diagrams';

import ActionPortModel from "../models/ActionPortModel";
import StagePortModel from "../models/StagePortModel";


class PortWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            selected: false
        };
    }

    editSelected = (selected) => {
        return () => this.setState({ selected });
    };

    // TODO Style + I18N
    render() {
        const { t } = this.props;
        let classes = 'port custom-port ';
        if (this.props.model instanceof ActionPortModel) {
            classes += 'action-port ';
        } else if (this.props.model instanceof StagePortModel) {
            classes += 'stage-port ';
        }
        if (this.state.selected) {
            classes += 'selected ';
        }
        return (
            <div
                /* TODO propagate style or class ??? */
                style={this.props.style || {}}
                className={classes}
                title={this.props.model.label}
                onMouseEnter={this.editSelected(true)}
                onMouseLeave={this.editSelected(false)}
                data-name={this.props.model.name}
                data-nodeid={this.props.model.getParent().getID()}
            />
        )
    }

}

PortWidget.propTypes = {
    model: PropTypes.instanceOf(SRD.PortModel).isRequired
};

export default PortWidget
