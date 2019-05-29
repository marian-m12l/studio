/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";


class TrayItemWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {};
    }


    render() {
        return (
            <div
                style={{borderColor: this.props.color}}
                draggable={true}
                onDragStart={event => {
                    event.dataTransfer.setData("storm-diagram-node", JSON.stringify(this.props.model));
                }}
                className="tray-item"
            >
                {this.props.name}
            </div>
        );
    }

}

TrayItemWidget.propTypes = {
    name: PropTypes.string.isRequired,
    color: PropTypes.string.isRequired,
    model: PropTypes.object.isRequired
};

export default TrayItemWidget;
