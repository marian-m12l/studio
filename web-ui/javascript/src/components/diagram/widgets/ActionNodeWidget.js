/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import * as SRD from 'storm-react-diagrams';
import {withTranslation} from "react-i18next";

import EditableHeader from './composites/EditableHeader';
import ActionNodeModel from "../models/ActionNodeModel";


class ActionNodeWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            beingEdited: false
        };
    }

    toggleEdit = () => {
        this.setState({
            beingEdited: !this.state.beingEdited
        });
    };

    editName = (event) => {
        this.props.node.name = event.target.value;
        this.props.updateCanvas();
        this.forceUpdate();
    };

    addOption = () => {
        this.props.node.addOption();
        this.props.updateCanvas();
        this.forceUpdate();
    };

    removeOption = () => {
        this.props.node.removeOption();
        this.props.updateCanvas();
        this.forceUpdate();
    };

    render() {
        const { t } = this.props;
        return (
            <div className='basic-node action-node'>
                <EditableHeader beingEdited={this.state.beingEdited} onToggleEdit={this.toggleEdit} onChange={this.editName} node={this.props.node} />
                <div className='title'>
                    <span className='name'>{t('editor.diagram.action.options')}</span>
                    {this.props.node.optionsIn.length > 0 && <span className='btn btn-xs glyphicon glyphicon-minus' onClick={this.removeOption} />}
                    <span className='btn btn-xs glyphicon glyphicon-plus' onClick={this.addOption} />
                </div>
                <div className='ports'>
                    <div className='in'>
                        {this.props.node.optionsIn.map(opt => <SRD.DefaultPortLabel key={opt.getID()} model={opt} />)}
                        <SRD.DefaultPortLabel key={this.props.node.randomOptionIn.getID()} model={this.props.node.randomOptionIn} className="random" />
                    </div>
                    <div className='out'>
                        {this.props.node.optionsOut.map(opt => <SRD.DefaultPortLabel key={opt.getID()} model={opt} />)}
                    </div>
                </div>
            </div>
        );
    }

}

ActionNodeWidget.propTypes = {
    node: PropTypes.instanceOf(ActionNodeModel).isRequired,
    diagramEngine: PropTypes.instanceOf(SRD.DiagramEngine).isRequired,
    updateCanvas: PropTypes.func.isRequired
};

export default withTranslation()(ActionNodeWidget);
