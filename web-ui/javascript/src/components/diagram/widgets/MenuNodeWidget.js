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

import MenuNodeModel from "../models/MenuNodeModel";
import {setViewerAction, setViewerDiagram, setViewerStage, showViewer} from "../../../actions";
import EditableText from "./composites/EditableText";


class MenuNodeWidget extends React.Component {

    constructor(props) {
        super(props);
    }

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
        this.props.node.removeOption();
        this.props.updateCanvas();
        this.forceUpdate();
    };

    editDefaultOption = (idx) => {
        return (event) => {
            this.props.node.setDefaultOption(idx);
            this.props.updateCanvas();
            this.forceUpdate();
        }
    };

    editOptionName = (idx) => {
        return (event) => {
            this.props.node.setOptionName(idx, event.target.value);
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

    onDropQuestionAudio = (event) => {
        event.preventDefault();
        if (!event.dataTransfer.items && !event.dataTransfer.files) {
            // Ignore data transfer (e.g. node drop)
            return;
        }
        let file = this.getDroppedFile(event);
        this.editQuestionAudio(file);
    };

    showQuestionAudioFileSelector = () => {
        document.getElementById(`audio-upload-question-${this.props.node.getUuid()}`).click();
    };

    questionAudioFileSelected = (event) => {
        let file = event.target.files[0];
        console.log('Selected file name = ' + file.name);
        this.editQuestionAudio(file);
    };

    editQuestionAudio = (file) => {
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
            that.props.node.setQuestionAudio(reader.result);
            that.props.updateCanvas();
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    onDropOptionImage = (idx) => {
        return (event) => {
            event.preventDefault();
            if (!event.dataTransfer.items && !event.dataTransfer.files) {
                // Ignore data transfer (e.g. node drop)
                return;
            }
            let file = this.getDroppedFile(event);
            this.editOptionImage(idx, file);
        };
    };

    showOptionImageFileSelector = (idx) => {
        return () => document.getElementById(`image-upload-${this.props.node.getUuid()}-${idx}`).click();
    };

    optionImageFileSelected = (idx) => {
        return (event) => {
            let file = event.target.files[0];
            console.log('Selected file name = ' + file.name);
            this.editOptionImage(idx, file);
        };
    };

    editOptionImage = (idx, file) => {
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
            that.props.node.setOptionImage(idx, reader.result);
            that.props.updateCanvas();
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    onDropOptionAudio = (idx) => {
        return (event) => {
            event.preventDefault();
            if (!event.dataTransfer.items && !event.dataTransfer.files) {
                // Ignore data transfer (e.g. node drop)
                return;
            }
            let file = this.getDroppedFile(event);
            this.editOptionAudio(idx, file);
        };
    };

    showOptionAudioFileSelector = (idx) => {
        return () => document.getElementById(`audio-upload-${this.props.node.getUuid()}-${idx}`).click();
    };

    optionAudioFileSelected = (idx) => {
        return (event) => {
            let file = event.target.files[0];
            console.log('Selected file name = ' + file.name);
            this.editOptionAudio(idx, file);
        };
    };

    editOptionAudio = (idx, file) => {
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
            that.props.node.setOptionAudio(idx, reader.result);
            that.props.updateCanvas();
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    isPreviewable = () => {
        return this.props.node.getQuestionAudio()
            && this.props.node.optionsStages.filter((o, idx) => !this.props.node.getOptionImage(idx) || !this.props.node.getOptionAudio(idx)).length === 0;
    };

    openViewer = (e) => {
        let viewingNode = this.props.node;
        this.props.setViewerDiagram(this.props.diagramEngine.diagramModel);
        let [stage, action] = viewingNode.onEnter(viewingNode.fromPort, this.props.diagramEngine.diagramModel);
        this.props.setViewerStage(stage);
        this.props.setViewerAction(action);
        this.props.showViewer();
    };

    // TODO Style + custom ports + I18N
    render() {
        const { t } = this.props;
        return (
            <div className='user-friendly-node menu-node'>
                <div style={{display: 'flex', flexDirection: 'row', backgroundColor: 'blue'}}>
                    <div style={{flexBasis: '20px', flexGrow: 0, flexShrink: 0, writingMode: 'vertical-lr', textOrientation: 'upright', backgroundColor: 'red'}}>
                        {this.props.node.fromPort && <SRD.DefaultPortLabel model={this.props.node.fromPort}/>}
                    </div>
                    <div style={{display: 'flex', flexDirection: 'column', flexGrow: 1, backgroundColor: 'green'}}>
                        <div style={{flexBasis: '20px', flexGrow: 0, flexShrink: 0, backgroundColor: 'lightgreen'}}>
                            <div>MENU NODE</div>
                            <div><EditableText value={this.props.node.getName()} onChange={this.editName}/></div>
                            <p>Question ?</p>
                            <div className="assets">
                                <input type="file" id={`audio-upload-question-${this.props.node.getUuid()}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.questionAudioFileSelected} />
                                <div className="audio-asset"
                                     title={t('editor.diagram.stage.audio')}
                                     onClick={this.showQuestionAudioFileSelector}
                                     onDrop={this.onDropQuestionAudio}
                                     onDragOver={event => { event.preventDefault(); }}>
                                    {!this.props.node.getQuestionAudio() && <span className="dropzone glyphicon glyphicon-music"/>}
                                    {this.props.node.getQuestionAudio() && <span className="dropzone glyphicon glyphicon-play"/>}
                                </div>
                            </div>
                        </div>
                        <div style={{flexBasis: '20px', flexGrow: 0, flexShrink: 0, backgroundColor: 'pink'}}>
                            <p>Options ?</p>
                            {this.props.node.optionsStages.length > 1 && <span className='btn btn-xs glyphicon glyphicon-minus' onClick={this.removeOption} />}
                            <span className='btn btn-xs glyphicon glyphicon-plus' onClick={this.addOption} />
                        </div>
                        <div style={{display: 'flex', flexDirection: 'column', flexGrow: 1, backgroundColor: 'lightblue'}}>
                            {this.props.node.optionsStages.map((option, idx) =>
                                <div key={`menu-option-${idx}`} style={{flexBasis: '20px', flexGrow: 0, flexShrink: 0, backgroundColor: 'yellow'}}>
                                    <div>
                                        <input type="radio" value={`menu-option-${idx}`} checked={this.props.node.getDefaultOption() === idx} onChange={this.editDefaultOption(idx)}/>
                                        <EditableText value={this.props.node.getOptionName(idx)} onChange={this.editOptionName(idx)}/>
                                    </div>
                                    <div className="assets" style={{flexGrow: 3}}>
                                        <input type="file" id={`image-upload-${this.props.node.getUuid()}-${idx}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.optionImageFileSelected(idx)} />
                                        <div className="image-asset"
                                             title={t('editor.diagram.stage.image')}
                                             onClick={this.showOptionImageFileSelector(idx)}
                                             onDrop={this.onDropOptionImage(idx)}
                                             onDragOver={event => { event.preventDefault(); }}>
                                            {!this.props.node.getOptionImage(idx) && <span className="dropzone glyphicon glyphicon-picture"/>}
                                            {this.props.node.getOptionImage(idx) && <img src={this.props.node.getOptionImage(idx)} className="dropzone" style={{height: '43px'}}/>}
                                        </div>
                                        <input type="file" id={`audio-upload-${this.props.node.getUuid()}-${idx}`} style={{visibility: 'hidden', position: 'absolute'}} onChange={this.optionAudioFileSelected(idx)} />
                                        <div className="audio-asset"
                                             title={t('editor.diagram.stage.audio')}
                                             onClick={this.showOptionAudioFileSelector(idx)}
                                             onDrop={this.onDropOptionAudio(idx)}
                                             onDragOver={event => { event.preventDefault(); }}>
                                            {!this.props.node.getOptionAudio(idx) && <span className="dropzone glyphicon glyphicon-music"/>}
                                            {this.props.node.getOptionAudio(idx) && <span className="dropzone glyphicon glyphicon-play"/>}
                                        </div>
                                    </div>
                                    <SRD.DefaultPortLabel model={this.props.node.optionsOut[idx]}/>
                                </div>
                            )}
                            {/* Default to random option */}
                            <div style={{flexBasis: '20px', flexGrow: 0, flexShrink: 0, backgroundColor: 'yellow'}}>
                                <div>
                                    <input type="radio" name="menu-option-random" value="menu-option-random" checked={this.props.node.getDefaultOption() === -1} onChange={this.editDefaultOption(-1)}/>
                                    <label htmlFor="menu-option-random">Random option</label>
                                </div>
                            </div>
                        </div>
                        {/* Global preview of the node */}
                        {this.isPreviewable() && <div className="preview"
                                                  title={t('editor.diagram.stage.preview')}
                                                  onClick={this.openViewer}>
                            <span className="dropzone glyphicon glyphicon-eye-open"/>
                        </div>}
                    </div>
                </div>
            </div>
        );
    }

}

MenuNodeWidget.propTypes = {
    node: PropTypes.instanceOf(MenuNodeModel).isRequired,
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
    )(MenuNodeWidget)
)
