/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {withTranslation} from "react-i18next";
import { DiagramEngine } from '@projectstorm/react-diagrams';
import {connect} from "react-redux";

import ActionNodeModel from "../models/ActionNodeModel";
import EditableText from "./composites/EditableText";
import StudioPortWidget from "./StudioPortWidget";


class ActionNodeWidget extends React.Component {

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

    getNodeErrors = () => {
        return this.props.errors[this.props.node.getID()];
    };

    getNodeErrorsTitle = () => {
        let nodeErrors = this.getNodeErrors();
        return nodeErrors ? Object.values(nodeErrors).join('\n') : null;
    };

    getNodeError = (key) => {
        let nodeErrors = this.getNodeErrors();
        return nodeErrors ? nodeErrors[key] : null;
    };

    render() {
        const { t } = this.props;
        return (
            <div className={`studio-node basic-node action-node ${this.props.selected && 'selected'} ${this.props.viewer.action.node === this.props.node && 'playing'} ${this.getNodeErrors() && 'error'}`} title={this.getNodeErrorsTitle()}>
                <div className="node-content">
                    <div className="node-title">
                        <div className="ellipsis">
                            <EditableText value={this.props.node.getName()} onChange={this.editName} engine={this.props.diagramEngine}/>
                        </div>
                    </div>
                    <div className="options">
                        <div>
                            <span className={`btn btn-xs glyphicon glyphicon-minus ${this.props.node.optionsIn.length <= 1 ? 'disabled' : ''}`} onClick={this.removeOption} title={t('editor.diagram.action.removeLastOption')} />
                            <span className='btn btn-xs glyphicon glyphicon-plus' onClick={this.addOption} title={t('editor.diagram.action.addOption')} />
                        </div>
                        {this.props.node.optionsIn.map((option, idx) =>
                            <div key={`action-option-${idx}`} className={`option ${this.props.viewer.action.node === this.props.node && this.props.viewer.action.index === idx && 'playing'}`}>
                                <div className={`delete ${this.props.node.optionsIn.length <= 1 ? 'disabled' : ''}`} title={t('editor.diagram.action.removeOption')} onClick={this.removeSpecificOption(idx)}/>
                                {option.label}
                                <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.optionsIn[idx]} className={`option-port-in ${this.getNodeError('optionsIn') ? 'error' : ''}`}/>
                                <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.optionsOut[idx]} className={`option-port-out ${this.getNodeError('optionsOut_'+idx) ? 'error' : ''}`}/>
                            </div>
                        )}
                        <div className="option">
                            {t('editor.diagram.action.random')}
                            <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.randomOptionIn} className={`option-port-in option-port-random ${this.getNodeError('optionsIn') ? 'error' : ''}`}/>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

}

ActionNodeWidget.propTypes = {
    node: PropTypes.instanceOf(ActionNodeModel).isRequired,
    diagramEngine: PropTypes.instanceOf(DiagramEngine).isRequired,
    updateCanvas: PropTypes.func.isRequired,
    selected: PropTypes.bool.isRequired
};

const mapStateToProps = (state, ownProps) => ({
    viewer: state.viewer,
    errors: state.editor.errors
});

const mapDispatchToProps = (dispatch, ownProps) => ({
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(ActionNodeWidget)
);
