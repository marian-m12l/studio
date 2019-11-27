/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import * as SRD from 'storm-react-diagrams';
import {withTranslation} from "react-i18next";

import ActionNodeModel from "../models/ActionNodeModel";
import EditableText from "./composites/EditableText";
import PortWidget from "./PortWidget";


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
        this.props.node.setName(event.target.value);
        this.props.updateCanvas();
        this.forceUpdate();
    };

    addOption = () => {
        this.props.node.addOption();
        this.props.updateCanvas();
        this.forceUpdate();
    };

    removeOption = () => {
        if (this.props.node.optionsIn.length > 1) {
            this.props.node.removeOption();
            this.props.updateCanvas();
            this.forceUpdate();
        }
    };

    removeSpecificOption = (idx) => {
        return () => {
            if (this.props.node.optionsIn.length > 1) {
                this.props.node.removeOption(idx);
                this.forceUpdate();
                this.props.updateCanvas();
            }
        };
    };

    render() {
        const { t } = this.props;
        return (
            <div className='studio-node basic-node action-node'>
                <div className="node-content">
                    <div className="node-title">
                        <div className="ellipsis">
                            <EditableText value={this.props.node.getName()} onChange={this.editName}/>
                        </div>
                    </div>
                    <div className="options">
                        <div>
                            <span className={`btn btn-xs glyphicon glyphicon-minus ${this.props.node.optionsIn.length <= 1 ? 'disabled' : ''}`} onClick={this.removeOption} title={t('editor.diagram.action.removeLastOption')} />
                            <span className='btn btn-xs glyphicon glyphicon-plus' onClick={this.addOption} title={t('editor.diagram.action.addOption')} />
                        </div>
                        {this.props.node.optionsIn.map((option, idx) =>
                            <div key={`action-option-${idx}`} className="option">
                                <div className={`delete ${this.props.node.optionsIn.length <= 1 ? 'disabled' : ''}`} title={t('editor.diagram.action.removeOption')} onClick={this.removeSpecificOption(idx)}/>
                                {option.label}
                                <PortWidget model={this.props.node.optionsIn[idx]} className="option-port-in"/>
                                <PortWidget model={this.props.node.optionsOut[idx]} className="option-port-out"/>
                            </div>
                        )}
                        <div className="option">
                            {t('editor.diagram.action.random')}
                            <PortWidget model={this.props.node.randomOptionIn} className="option-port-in option-port-random"/>
                        </div>
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
