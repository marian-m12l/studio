/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {withTranslation} from "react-i18next";
import {toast} from "react-toastify";
import {connect} from "react-redux";
import { DiagramEngine } from '@projectstorm/react-diagrams';

import StoryNodeModel from "../models/StoryNodeModel";
import {setViewerAction, setViewerDiagram, setViewerStage, showViewer} from "../../../actions";
import EditableText from "./composites/EditableText";
import StudioPortWidget from "./StudioPortWidget";


class StoryNodeWidget extends React.Component {

    editName = (event) => {
        this.props.node.setName(event.target.value);
        this.props.updateCanvas();
        this.forceUpdate();
    };

    toggleCustomOkTransition = () => {
        this.props.node.setCustomOkTransition(!this.props.node.customOkTransition);
        this.props.updateCanvas();
        this.forceUpdate();
    };

    toggleCustomHomeTransition = () => {
        this.props.node.setCustomHomeTransition(!this.props.node.customHomeTransition);
        this.props.updateCanvas();
        this.forceUpdate();
    };

    toggleDisableHome = () => {
        this.props.node.setDisableHome(!this.props.node.disableHome);
        this.props.updateCanvas();
        this.forceUpdate();
    };

    getDroppedFile = (event) => {
        let file = null;
        if (event.dataTransfer.items) {
            // Use first file only
            // If dropped items aren't files, reject them
            if (event.dataTransfer.items[0].kind === 'file') {
                file = event.dataTransfer.items[0].getAsFile();
                console.log('Dropped item file name = ' + file.name);
            } else {
                // Ignore non-file item
                return;
            }
        } else {
            // Use first file only
            file = event.dataTransfer.files[0];
            console.log('Dropped file name = ' + file.name);
        }
        return file;
    };

    onDropAudio = (event) => {
        event.preventDefault();
        if (!event.dataTransfer.items && !event.dataTransfer.files) {
            // Ignore data transfer (e.g. node drop)
            return;
        }
        let file = this.getDroppedFile(event);
        this.editAudio(file);
    };

    showAudioFileSelector = () => {
        document.getElementById(`audio-upload-${this.props.node.getUuid()}`).click();
    };

    audioFileSelected = (event) => {
        let file = event.target.files[0];
        if (file) {
            console.log('Selected file name = ' + file.name);
            this.editAudio(file);
        }
    };

    editAudio = (file) => {
        const { t } = this.props;
        if (!file) {
            return;
        }
        console.log(file.type);
        if (['audio/wav', 'audio/x-wav', 'audio/mp3', 'audio/mpeg', 'audio/ogg', 'video/ogg'].indexOf(file.type) === -1) {
            toast.error(t('toasts.editor.audioAssetWrongType'));
            return;
        }

        let reader = new FileReader();
        let that = this;
        reader.addEventListener("load", function () {
            that.props.node.setAudio(reader.result);
            that.props.updateCanvas();
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    resetAudio = (event) => {
        event.preventDefault();
        event.stopPropagation();
        this.props.node.setAudio(null);
        this.props.updateCanvas();
        this.forceUpdate();
    };

    isPreviewable = () => {
        return this.props.node.getAudio();
    };

    openViewer = (e) => {
        if (this.isPreviewable()) {
            let viewingNode = this.props.node;
            this.props.setViewerDiagram(this.props.diagramEngine.getModel());
            this.props.setViewerStage(viewingNode);
            this.props.setViewerAction({
                node: null,
                index: null
            });
            this.props.showViewer();
        }
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

    // TODO Style (advanced options)
    render() {
        const { t } = this.props;
        return (
            <div className={`studio-node user-friendly-node story-node ${this.props.selected && 'selected'} ${this.props.viewer.stage === this.props.node && 'playing'} ${this.getNodeErrors() && 'error'}`} title={this.getNodeErrorsTitle()}>
                <div className="node-header">
                    <span className="dropzone glyphicon glyphicon-headphones" title={t('editor.tray.story')}/>
                </div>
                <div className="node-content">
                    <div className="node-title">
                        <div className="ellipsis">
                            <EditableText value={this.props.node.getName()} onChange={this.editName} engine={this.props.diagramEngine}/>
                        </div>
                        <div className={`preview ${!this.isPreviewable() ? 'disabled' : ''}`} title={t('editor.diagram.stage.preview')} onClick={this.openViewer}>
                            <span className="glyphicon glyphicon-eye-open"/>
                        </div>
                    </div>
                    <div className="node-row">
                        <div className="assets-and-options">
                            <div className="asset">
                                <input type="file" id={`audio-upload-${this.props.node.getUuid()}`} onChange={this.audioFileSelected} />
                                <div className="dropzone-asset audio-asset"
                                     title={t('editor.diagram.stage.audio')}
                                     onClick={this.showAudioFileSelector}
                                     onDrop={this.onDropAudio}
                                     onDragOver={event => { event.preventDefault(); }}>
                                    {!this.props.node.getAudio() && <span className="dropzone glyphicon glyphicon-music"/>}
                                    {this.props.node.getAudio() && <>
                                        <div className="delete" title={t('editor.diagram.stage.resetAudio')} onClick={this.resetAudio}/>
                                        <span className="dropzone glyphicon glyphicon-play"/>
                                    </>}
                                </div>
                            </div>
                            <div className="options">
                                <span title={t('editor.diagram.story.options.customok')} className={'btn btn-xs glyphicon glyphicon-play' + (this.props.node.customOkTransition ? ' active' : '')} onClick={this.toggleCustomOkTransition}/>
                                {!this.props.node.disableHome &&<span title={t('editor.diagram.story.options.customhome')} className={'btn btn-xs glyphicon glyphicon-home' + (this.props.node.customHomeTransition ? ' active' : '')} onClick={this.toggleCustomHomeTransition}/>}
                                <span title={t('editor.diagram.story.options.disablehome')} className={'btn btn-xs glyphicon glyphicon-home' + (this.props.node.disableHome ? ' active' : '')}
                                      style={{textDecorationLine: 'line-through', textDecorationColor: 'red', textDecorationStyle: 'double', textDecorationThickness: '3px'}} onClick={this.toggleDisableHome}/>
                            </div>
                        </div>
                        {(this.props.node.okPort || this.props.node.homePort) && <div className='ports'>
                            <div className="output-port">
                                {this.props.node.okPort && <>
                                    <span title={t('editor.diagram.story.options.customok')} className={'glyphicon glyphicon-play'}/>
                                    <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.okPort} className={`ok-port ${this.getNodeError('okPort') ? 'error' : ''}`}/>
                                </>}
                            </div>
                            <div className="output-port">
                                {this.props.node.homePort && <>
                                    <span title={t('editor.diagram.story.options.customhome')} className={'glyphicon glyphicon-home'}/>
                                    <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.homePort} className={`home-port ${this.getNodeError('homePort') ? 'error' : ''}`}/>
                                </>}
                            </div>
                        </div>}
                    </div>
                </div>
                {this.props.node.fromPort && <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.fromPort} className={`from-port ${this.getNodeError('fromPort') ? 'error' : ''}`}/>}
            </div>
        );
    }

}

StoryNodeWidget.propTypes = {
    node: PropTypes.instanceOf(StoryNodeModel).isRequired,
    diagramEngine: PropTypes.instanceOf(DiagramEngine).isRequired,
    updateCanvas: PropTypes.func.isRequired,
    selected: PropTypes.bool.isRequired
};

const mapStateToProps = (state, ownProps) => ({
    viewer: state.viewer,
    errors: state.editor.errors
});

const mapDispatchToProps = (dispatch, ownProps) => ({
    showViewer: () => dispatch(showViewer()),
    setViewerDiagram: (diagram) => dispatch(setViewerDiagram(diagram)),
    setViewerStage: (stage) => dispatch(setViewerStage(stage)),
    setViewerAction: (action) => dispatch(setViewerAction(action))
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(StoryNodeWidget)
)
