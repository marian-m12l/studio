/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {connect} from "react-redux";
import { toast } from 'react-toastify';
import * as SRD from 'storm-react-diagrams';
import {withTranslation} from "react-i18next";

import EditableHeader from './composites/EditableHeader';
import StageNodeModel from "../models/StageNodeModel";
import {showViewer, setViewerDiagram, setViewerStage, setViewerAction} from "../../../actions";


class StageNodeWidget extends React.Component {

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

    toggleSquareOne = () => {
        const { t } = this.props;
        // Make sure there is not already an entry point in the diagram
        if (!this.props.node.isSquareOne() && this.props.diagramEngine.diagramModel.getEntryPoint()) {
            toast.error(t('toasts.editor.tooManyEntryPoints'));
            return;
        }
        this.props.node.setSquareOne(!this.props.node.isSquareOne());
        this.props.updateCanvas();
        this.forceUpdate();
    };

    toggleControl = (control) => {
        return () => {
            this.props.node.toggleControl(control);
            this.props.updateCanvas();
            this.forceUpdate();
        };
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

    onDropImage = (event) => {
        event.preventDefault();
        if (!event.dataTransfer.items && !event.dataTransfer.files) {
            // Ignore data transfer (e.g. node drop)
            return;
        }
        let file = this.getDroppedFile(event);
        this.editImage(file);
    };

    showImageFileSelector = () => {
        document.getElementById(`image-upload-${this.props.node.getUuid()}`).click();
    };

    imageFileSelected = (event) => {
        let file = event.target.files[0];
        if (file) {
            console.log('Selected file name = ' + file.name);
            this.editImage(file);
        }
    };

    editImage = (file) => {
        const { t } = this.props;
        if (!file) {
            return;
        }
        if (['image/bmp', 'image/jpeg', 'image/png'].indexOf(file.type) === -1) {
            toast.error(t('toasts.editor.imageAssetWrongType'));
            return;
        }

        let reader = new FileReader();
        let that = this;
        reader.addEventListener("load", function () {
            that.props.node.setImage(reader.result);
            that.props.updateCanvas();
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    resetImage = (event) => {
        event.preventDefault();
        event.stopPropagation();
        this.props.node.setImage(null);
        this.props.updateCanvas();
        this.forceUpdate();
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

    openViewer = (e) => {
        let viewingNode = this.props.node;
        this.props.setViewerDiagram(this.props.diagramEngine.diagramModel);
        this.props.setViewerStage(viewingNode);
        let fromLinks = Object.values(viewingNode.fromPort.getLinks());
        if (fromLinks.length > 0) {
            let firstActionNode = fromLinks[0].getSourcePort().getParent();
            this.props.setViewerAction({
                node: firstActionNode,
                index: firstActionNode.optionsOut.indexOf(fromLinks[0].getSourcePort())
            });
        } else {
            this.props.setViewerAction({
                node: null,
                index: null
            });
        }
        this.props.showViewer();
    };

    render() {
        const { t } = this.props;
        return (
            <div className={`basic-node stage-node ${this.props.node.squareOne && 'square-one'}`}>
                <EditableHeader beingEdited={this.state.beingEdited} onToggleEdit={this.toggleEdit} onChange={this.editName} node={this.props.node} />
                <div className="controls">
                    <span title={t('editor.diagram.stage.squareone')} className={'btn btn-xs' + (this.props.node.isSquareOne() ? ' active' : '')} onClick={this.toggleSquareOne}>&#x2776;</span>
                    <span title={t('editor.diagram.stage.controls.wheel')} className={'btn btn-xs glyphicon glyphicon-resize-horizontal' + (this.props.node.getControls().wheel ? ' active' : '')} onClick={this.toggleControl('wheel')}/>
                    <span title={t('editor.diagram.stage.controls.ok')} className={'btn btn-xs glyphicon glyphicon-ok' + (this.props.node.getControls().ok ? ' active' : '')} onClick={this.toggleControl('ok')}/>
                    <span title={t('editor.diagram.stage.controls.home')} className={'btn btn-xs glyphicon glyphicon-home' + (this.props.node.getControls().home ? ' active' : '')} onClick={this.toggleControl('home')}/>
                    <span title={t('editor.diagram.stage.controls.pause')} className={'btn btn-xs glyphicon glyphicon-pause' + (this.props.node.getControls().pause ? ' active' : '')} onClick={this.toggleControl('pause')}/>
                    <span title={t('editor.diagram.stage.controls.autoplay')} className={'btn btn-xs glyphicon glyphicon-play' + (this.props.node.getControls().autoplay ? ' active' : '')} onClick={this.toggleControl('autoplay')}/>
                </div>
                <div className="assets">
                    <input type="file" id={`image-upload-${this.props.node.getUuid()}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.imageFileSelected} />
                    <div className="image-asset"
                         title={t('editor.diagram.stage.image')}
                         onClick={this.showImageFileSelector}
                         onDrop={this.onDropImage}
                         onDragOver={event => { event.preventDefault(); }}>
                        {!this.props.node.getImage() && <span className="dropzone glyphicon glyphicon-picture"/>}
                        {this.props.node.getImage() && <>
                            <div className="delete" title={t('editor.diagram.stage.resetImage')} onClick={this.resetImage}/>
                            <img src={this.props.node.getImage()} className="dropzone" style={{height: '43px'}}/>
                        </>}
                    </div>
                    <input type="file" id={`audio-upload-${this.props.node.getUuid()}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.audioFileSelected} />
                    <div className="audio-asset"
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
                    {(this.props.node.getImage() || this.props.node.getAudio()) && <div className="preview"
                                                                              title={t('editor.diagram.stage.preview')}
                                                                              onClick={this.openViewer}>
                        <span className="dropzone glyphicon glyphicon-eye-open"/>
                    </div>}
                </div>
                <div className='ports'>
                    <div className='out'>
                        {(this.props.node.getControls().ok || this.props.node.getControls().autoplay) && <SRD.DefaultPortLabel model={this.props.node.okPort}/>}
                        {this.props.node.getControls().home && <SRD.DefaultPortLabel model={this.props.node.homePort}/>}
                    </div>
                </div>
            </div>
        );
    }

}

StageNodeWidget.propTypes = {
    node: PropTypes.instanceOf(StageNodeModel).isRequired,
    diagramEngine: PropTypes.instanceOf(SRD.DiagramEngine).isRequired,
    updateCanvas: PropTypes.func.isRequired
};

const mapStateToProps = (state, ownProps) => ({
    viewer: state.viewer
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
    )(StageNodeWidget)
)
