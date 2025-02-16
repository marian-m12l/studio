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

import CoverNodeModel from "../models/CoverNodeModel";
import {setViewerAction, setViewerDiagram, setViewerStage, showViewer} from "../../../actions";
import EditableText from "./composites/EditableText";
import StudioPortWidget from "./StudioPortWidget";


class CoverNodeWidget extends React.Component {

    editName = (event) => {
        this.props.node.setName(event.target.value);
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
            let image = new Image();
            image.onload = function() {
                // Check image dimensions
                if(image.width !== 320 || image.height !== 240) {
                    toast.error(t('toasts.editor.imageAssetWrongDimensions'));
                    return;
                }
                // Update node image
                that.props.node.setImage(reader.result);
                that.props.updateCanvas();
                that.forceUpdate();
            };
            image.src = reader.result;
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

    isPreviewable = () => {
        return this.props.node.getImage() || this.props.node.getAudio();
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

    render() {
        const { t } = this.props;
        return (
            <div className={`studio-node user-friendly-node cover-node ${this.props.selected && 'selected'} ${this.props.viewer.stage === this.props.node && 'playing'} ${this.getNodeErrors() && 'error'}`} title={this.getNodeErrorsTitle()}>
                <div className="node-header">
                    <span className="dropzone glyphicon glyphicon-book" title={t('editor.tray.cover')}/>
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
                    <div className="assets">
                        <div className="asset asset-left">
                            <input type="file" id={`image-upload-${this.props.node.getUuid()}`} onChange={this.imageFileSelected} />
                            <div className="dropzone-asset image-asset"
                                 title={t('editor.diagram.stage.image')}
                                 onClick={this.showImageFileSelector}
                                 onDrop={this.onDropImage}
                                 onDragOver={event => { event.preventDefault(); }}>
                                {!this.props.node.getImage() && <span className="dropzone glyphicon glyphicon-picture"/>}
                                {this.props.node.getImage() && <>
                                    <div className="delete" title={t('editor.diagram.stage.resetImage')} onClick={this.resetImage}/>
                                    <img src={this.props.node.getImage()} alt="" className="dropzone"/>
                                </>}
                            </div>
                        </div>
                        <div className="asset right">
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
                    </div>
                </div>
                {this.props.node.okPort && <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.okPort} className={`ok-port ${this.getNodeError('okPort') ? 'error' : ''}`}/>}
            </div>
        );
    }

}

CoverNodeWidget.propTypes = {
    node: PropTypes.instanceOf(CoverNodeModel).isRequired,
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
    )(CoverNodeWidget)
)
