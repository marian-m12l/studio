/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import * as SRD from 'storm-react-diagrams';
import {withTranslation} from "react-i18next";
import {toast} from "react-toastify";
import {connect} from "react-redux";

import StoryNodeModel from "../models/StoryNodeModel";
import {setViewerAction, setViewerDiagram, setViewerStage, showViewer} from "../../../actions";
import EditableText from "./composites/EditableText";


class StoryNodeWidget extends React.Component {

    constructor(props) {
        super(props);
    }

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
        console.log('Selected file name = ' + file.name);
        this.editAudio(file);
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

    openViewer = (e) => {
        let viewingNode = this.props.node;
        this.props.setViewerDiagram(this.props.diagramEngine.diagramModel);
        this.props.setViewerStage(viewingNode);
        this.props.setViewerAction({
            node: null,
            index: null
        });
        this.props.showViewer();
    };

    // TODO Style + custom ports + I18N
    render() {
        const { t } = this.props;
        return (
            <div className='user-friendly-node story-node'>
                <div style={{display: 'flex', flexDirection: 'row', backgroundColor: 'blue'}}>
                    <div style={{flexBasis: '20px', flexGrow: 0, flexShrink: 0, writingMode: 'vertical-lr', textOrientation: 'upright', backgroundColor: 'red'}}>
                        {this.props.node.fromPort && <SRD.DefaultPortLabel model={this.props.node.fromPort}/>}
                    </div>
                    <div style={{display: 'flex', flexDirection: 'row', flexGrow: 1, backgroundColor: 'green'}}>
                        <div style={{flexGrow: 1, backgroundColor: 'lightgreen'}}>
                            <div>STORY NODE</div>
                            <div><EditableText value={this.props.node.getName()} onChange={this.editName}/></div>
                            <div className="assets">
                                <input type="file" id={`audio-upload-${this.props.node.getUuid()}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.audioFileSelected} />
                                <div className="audio-asset"
                                     title={t('editor.diagram.stage.audio')}
                                     onClick={this.showAudioFileSelector}
                                     onDrop={this.onDropAudio}
                                     onDragOver={event => { event.preventDefault(); }}>
                                    {!this.props.node.getAudio() && <span className="dropzone glyphicon glyphicon-music"/>}
                                    {this.props.node.getAudio() && <span className="dropzone glyphicon glyphicon-play"/>}
                                </div>
                                {this.props.node.getAudio() && <div className="preview"
                                                                    title={t('editor.diagram.stage.preview')}
                                                                    onClick={this.openViewer}>
                                    <span className="dropzone glyphicon glyphicon-eye-open"/>
                                </div>}
                            </div>
                            <hr/>
                            <div className="options">
                                <span title={t('editor.diagram.story.options.customok')} className={'btn btn-xs glyphicon glyphicon-ok' + (this.props.node.customOkTransition ? ' active' : '')} onClick={this.toggleCustomOkTransition}/>
                                {!this.props.node.disableHome &&<span title={t('editor.diagram.story.options.customhome')} className={'btn btn-xs glyphicon glyphicon-home' + (this.props.node.customHomeTransition ? ' active' : '')} onClick={this.toggleCustomHomeTransition}/>}
                                <span title={t('editor.diagram.story.options.disablehome')} className={'btn btn-xs glyphicon glyphicon-home' + (this.props.node.disableHome ? ' active' : '')}
                                      style={{textDecorationLine: 'line-through', textDecorationColor: 'red', textDecorationStyle: 'double', textDecorationThickness: '3px'}} onClick={this.toggleDisableHome}/>
                            </div>
                        </div>
                        {(this.props.node.okPort || this.props.node.homePort) && <div style={{flexBasis: '20px', flexGrow: 0, flexShrink: 0, writingMode: 'vertical-lr', textOrientation: 'upright', backgroundColor: 'lightblue'}}>
                            <div className='ports'>
                                <div className='out'>
                                    {this.props.node.okPort && <SRD.DefaultPortLabel model={this.props.node.okPort}/>}
                                    {this.props.node.homePort && <SRD.DefaultPortLabel model={this.props.node.homePort}/>}
                                </div>
                            </div>
                        </div>}
                    </div>
                </div>
            </div>
        );
    }

}

StoryNodeWidget.propTypes = {
    node: PropTypes.instanceOf(StoryNodeModel).isRequired,
    diagramEngine: PropTypes.instanceOf(SRD.DiagramEngine).isRequired,
    updateCanvas: PropTypes.func.isRequired
};

const mapStateToProps = (state, ownProps) => ({
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
