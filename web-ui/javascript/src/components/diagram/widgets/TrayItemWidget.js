/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {withTranslation} from "react-i18next";


class TrayItemWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            dragging: false
        };
    }

    render() {
        const { t } = this.props;
        let classes = 'tray-item ';
        if (this.state.dragging) {
            classes += 'dragging ';
        }
        if (this.props.className) {
            classes += this.props.className
        }
        return (
            <div
                draggable={true}
                onDragStart={event => {
                    event.dataTransfer.setData("storm-diagram-node", JSON.stringify(this.props.model));
                    this.setState({dragging: true});
                }}
                onDragEnd={event => {
                    this.setState({dragging: false});
                }}
                className={classes}
            >
                {this.props.helpClicked && <button onClick={this.props.helpClicked} title={t('editor.tray.help')} className="help glyphicon glyphicon-info-sign"/>}
                {this.props.children}
            </div>
        );
    }

}

TrayItemWidget.propTypes = {
    children: PropTypes.node.isRequired,
    model: PropTypes.object.isRequired,
    helpClicked: PropTypes.func,
    className: PropTypes.string
};

export default withTranslation()(TrayItemWidget);
