/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from 'prop-types';


class TrayWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {};
    }


    render() {
        return (
            <div className="tray">
                <h4>Drag to add</h4>
                {this.props.children}
            </div>
        );
    }

}

TrayWidget.propTypes = {
    children: PropTypes.node.isRequired
};

export default TrayWidget;
