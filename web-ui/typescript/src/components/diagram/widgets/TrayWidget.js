/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {withTranslation} from "react-i18next";


class TrayWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {};
    }


    render() {
        const { t } = this.props;
        return (
            <div className="tray">
                <h4>{t('editor.tray.title')}</h4>
                {this.props.children}
            </div>
        );
    }

}

TrayWidget.propTypes = {
    children: PropTypes.node.isRequired
};

export default withTranslation()(TrayWidget);
