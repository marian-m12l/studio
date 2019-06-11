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
        this.props.node.name = event.target.value;
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
        if (file.type !== 'image/bmp') {
            toast.error("Image asset must be in bitmap format.");
            return;
        }
        this.editImage(file);
    };

    showImageFileSelector = () => {
        document.getElementById(`image-upload-${this.props.node.uuid}`).click();
    };

    imageFileSelected = (event) => {
        let file = event.target.files[0];
        console.log('Selected file name = ' + file.name);
        if (file.type !== 'image/bmp') {
            toast.error("Image asset must be in bitmap format.");
            return;
        }
        this.editImage(file);
    };

    editImage = (file) => {
        let reader = new FileReader();
        let that = this;
        reader.addEventListener("load", function () {
            that.props.node.image = reader.result;
            that.props.updateCanvas();
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    onDropAudio = (event) => {
        event.preventDefault();
        if (!event.dataTransfer.items && !event.dataTransfer.files) {
            // Ignore data transfer (e.g. node drop)
            return;
        }
        let file = this.getDroppedFile(event);
        if (file.type !== 'audio/x-wav') {
            toast.error("Audio asset must be in Wave format.");
            return;
        }
        this.editAudio(file);
    };

    showAudioFileSelector = () => {
        document.getElementById(`audio-upload-${this.props.node.uuid}`).click();
    };

    audioFileSelected = (event) => {
        let file = event.target.files[0];
        console.log('Selected file name = ' + file.name);
        if (file.type !== 'audio/x-wav') {
            toast.error("Audio asset must be in Wave format.");
            return;
        }
        this.editAudio(file);
    };

    editAudio = (file) => {
        let reader = new FileReader();
        let that = this;
        reader.addEventListener("load", function () {
            that.props.node.audio = reader.result;
            that.props.updateCanvas();
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
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
        }
        this.props.showViewer();
    };

    render() {
        return (
            <div className={`basic-node stage-node ${this.props.node.squareOne && 'square-one'}`}>
                <EditableHeader beingEdited={this.state.beingEdited} onToggleEdit={this.toggleEdit} onChange={this.editName} node={this.props.node} />
                <div className="controls">
                    <span title="Allow wheel selection" className={'btn btn-xs glyphicon glyphicon-resize-horizontal' + (this.props.node.controls.wheel ? ' active' : '')} onClick={this.toggleControl('wheel')}/>
                    <span title="Allow OK button" className={'btn btn-xs glyphicon glyphicon-ok' + (this.props.node.controls.ok ? ' active' : '')} onClick={this.toggleControl('ok')}/>
                    <span title="Allow HOME button" className={'btn btn-xs glyphicon glyphicon-home' + (this.props.node.controls.home ? ' active' : '')} onClick={this.toggleControl('home')}/>
                    <span title="Allow PAUSE button" className={'btn btn-xs glyphicon glyphicon-pause' + (this.props.node.controls.pause ? ' active' : '')} onClick={this.toggleControl('pause')}/>
                    <span title="Enable autoplay" className={'btn btn-xs glyphicon glyphicon-play' + (this.props.node.controls.autoplay ? ' active' : '')} onClick={this.toggleControl('autoplay')}/>
                </div>
                <div className="assets">
                    <input type="file" id={`image-upload-${this.props.node.uuid}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.imageFileSelected} />
                    <div className="image-asset"
                         title="Image asset"
                         onClick={this.showImageFileSelector}
                         onDrop={this.onDropImage}
                         onDragOver={event => { event.preventDefault(); }}>
                        {!this.props.node.image && <span className="dropzone glyphicon glyphicon-picture"/>}
                        {this.props.node.image && <img src={this.props.node.image} className="dropzone" style={{height: '43px'}}/>}
                    </div>
                    <input type="file" id={`audio-upload-${this.props.node.uuid}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.audioFileSelected} />
                    <div className="audio-asset"
                         title="Audio asset"
                         onClick={this.showAudioFileSelector}
                         onDrop={this.onDropAudio}
                         onDragOver={event => { event.preventDefault(); }}>
                        {!this.props.node.audio && <span className="dropzone glyphicon glyphicon-music"/>}
                        {this.props.node.audio && <span className="dropzone glyphicon glyphicon-play"/>}
                    </div>
                    {(this.props.node.image || this.props.node.audio) && <div className="preview"
                                                                              title="Preview"
                                                                              onClick={this.openViewer}>
                        <span className="dropzone glyphicon glyphicon-eye-open"/>
                    </div>}
                </div>
                <div className='ports'>
                    <div className='out'>
                        {(this.props.node.controls.ok || this.props.node.controls.autoplay) && <SRD.DefaultPortLabel model={this.props.node.okPort}/>}
                        {this.props.node.controls.home && <SRD.DefaultPortLabel model={this.props.node.homePort}/>}
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

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(StageNodeWidget)
