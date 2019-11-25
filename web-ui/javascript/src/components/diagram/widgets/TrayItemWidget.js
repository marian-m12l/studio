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
        let classes = 'tray-item ';
        if (this.props.className) {
            classes += this.props.className
        }
        return (
            <div
                draggable={true}
                onDragStart={event => {
                    event.dataTransfer.setData("storm-diagram-node", JSON.stringify(this.props.model));
                }}
                className={classes}
            >
                {this.props.children}
            </div>
        );
    }

}

TrayItemWidget.propTypes = {
    children: PropTypes.node.isRequired,
    model: PropTypes.object.isRequired,
    className: PropTypes.string
};

export default TrayItemWidget;
