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

import MenuNodeModel from "../models/MenuNodeModel";
import {setViewerAction, setViewerDiagram, setViewerStage, showViewer} from "../../../actions";
import EditableText from "./composites/EditableText";
import StudioPortWidget from "./StudioPortWidget";


class MenuNodeWidget extends React.Component {

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
        if (this.props.node.optionsStages.length > 1) {
            this.props.node.removeOption();
            this.props.updateCanvas();
            this.forceUpdate();
        }
    };

    removeSpecificOption = (idx) => {
        return () => {
            if (this.props.node.optionsStages.length > 1) {
                this.props.node.removeOption(idx);
                this.props.updateCanvas();
                this.forceUpdate();
            }
        };
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
        if (file) {
            console.log('Selected file name = ' + file.name);
            this.editQuestionAudio(file);
        }
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

    resetQuestionAudio = (event) => {
        event.preventDefault();
        event.stopPropagation();
        this.props.node.setQuestionAudio(null);
        this.props.updateCanvas();
        this.forceUpdate();
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
            if (file) {
                console.log('Selected file name = ' + file.name);
                this.editOptionImage(idx, file);
            }
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
            let image = new Image();
            image.onload = function() {
                // Check image dimensions
                if(image.width !== 320 || image.height !== 240) {
                    toast.error(t('toasts.editor.imageAssetWrongDimensions'));
                    return;
                }
                // Update option image
                that.props.node.setOptionImage(idx, reader.result);
                that.props.updateCanvas();
                that.forceUpdate();
            };
            image.src = reader.result;
        }, false);
        reader.readAsDataURL(file);
    };

    resetOptionImage = (idx) => {
        return (event) => {
            event.preventDefault();
            event.stopPropagation();
            this.props.node.setOptionImage(idx, null);
            this.props.updateCanvas();
            this.forceUpdate();
        };
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
            if (file) {
                console.log('Selected file name = ' + file.name);
                this.editOptionAudio(idx, file);
            }
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

    resetOptionAudio = (idx) => {
        return (event) => {
            event.preventDefault();
            event.stopPropagation();
            this.props.node.setOptionAudio(idx, null);
            this.props.updateCanvas();
            this.forceUpdate();
        };
    };

    isPreviewable = () => {
        return this.props.node.getQuestionAudio()
            && this.props.node.optionsStages.filter((o, idx) => !this.props.node.getOptionImage(idx) || !this.props.node.getOptionAudio(idx)).length === 0;
    };

    openViewer = (e) => {
        if (this.isPreviewable()) {
            let viewingNode = this.props.node;
            this.props.setViewerDiagram(this.props.diagramEngine.getModel());
            let [stage, action] = viewingNode.onEnter(viewingNode.fromPort, this.props.diagramEngine.getModel());
            this.props.setViewerStage(stage);
            this.props.setViewerAction(action);
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
            <div className={`studio-node user-friendly-node menu-node ${this.props.selected && 'selected'} ${this.props.viewer.stage && this.props.viewer.stage.parentNode && this.props.viewer.stage.parentNode === this.props.node && 'playing'} ${this.getNodeErrors() && 'error'}`} title={this.getNodeErrorsTitle()}>
                <div className="node-header">
                    <span className="dropzone glyphicon glyphicon-question-sign" title={t('editor.tray.menu')}/>
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
                    <div className="question-and-options">
                        <div className={`question ${this.props.viewer.stage && this.props.viewer.stage.parentNode && this.props.viewer.stage.parentNode === this.props.node && this.props.viewer.action.node !== this.props.node && 'playing'}`}>
                            <p>{t('editor.diagram.menu.question')}</p>
                            <div className="question-asset">
                                <input type="file" id={`audio-upload-question-${this.props.node.getUuid()}`} onChange={this.questionAudioFileSelected} />
                                <div className="dropzone-asset audio-asset"
                                     title={t('editor.diagram.stage.audio')}
                                     onClick={this.showQuestionAudioFileSelector}
                                     onDrop={this.onDropQuestionAudio}
                                     onDragOver={event => { event.preventDefault(); }}>
                                    {!this.props.node.getQuestionAudio() && <span className="dropzone glyphicon glyphicon-music"/>}
                                    {this.props.node.getQuestionAudio() && <>
                                        <div className="delete" title={t('editor.diagram.stage.resetAudio')} onClick={this.resetQuestionAudio}/>
                                        <span className="dropzone glyphicon glyphicon-play"/>
                                    </>}
                                </div>
                            </div>
                        </div>
                        <div className={`options ${this.props.viewer.stage && this.props.viewer.stage.parentNode && this.props.viewer.stage.parentNode === this.props.node && this.props.viewer.action.node === this.props.node && 'playing'}`}>
                            <p>{t('editor.diagram.menu.options')}</p>
                            <div>
                                <span className={`btn btn-xs glyphicon glyphicon-minus ${this.props.node.optionsStages.length <= 1 ? 'disabled' : ''}`} onClick={this.removeOption} title={t('editor.diagram.menu.removeLastOption')} />
                                <span className='btn btn-xs glyphicon glyphicon-plus' onClick={this.addOption} title={t('editor.diagram.menu.addOption')} />
                            </div>
                            {this.props.node.optionsStages.map((option, idx) =>
                                <div key={`menu-option-${idx}`} className={`option ${this.props.viewer.action.node === this.props.node && this.props.viewer.action.index === idx && 'playing'} ${this.getNodeError('assets_'+idx) ? 'error' : ''}`}>
                                    <div className={`delete ${this.props.node.optionsStages.length <= 1 ? 'disabled' : ''}`} title={t('editor.diagram.menu.removeOption')} onClick={this.removeSpecificOption(idx)}/>
                                    <div className="policy">
                                        <input type="radio" value={`menu-option-${idx}`} checked={this.props.node.getDefaultOption() === idx} onChange={this.editDefaultOption(idx)} title={t('editor.diagram.menu.defaultOption')}/>
                                    </div>
                                    <div className="name-and-assets">
                                        <div className="option-name">
                                            <EditableText value={this.props.node.getOptionName(idx)} onChange={this.editOptionName(idx)} engine={this.props.diagramEngine}/>
                                        </div>
                                        <div className="option-assets">
                                            <div className="asset asset-left">
                                                <input type="file" id={`image-upload-${this.props.node.getUuid()}-${idx}`} onChange={this.optionImageFileSelected(idx)} />
                                                <div className="dropzone-asset image-asset"
                                                     title={t('editor.diagram.stage.image')}
                                                     onClick={this.showOptionImageFileSelector(idx)}
                                                     onDrop={this.onDropOptionImage(idx)}
                                                     onDragOver={event => { event.preventDefault(); }}>
                                                    {!this.props.node.getOptionImage(idx) && <span className="dropzone glyphicon glyphicon-picture"/>}
                                                    {this.props.node.getOptionImage(idx) && <>
                                                        <div className="delete" title={t('editor.diagram.stage.resetImage')} onClick={this.resetOptionImage(idx)}/>
                                                        <img src={this.props.node.getOptionImage(idx)} alt="" className="dropzone"/>
                                                    </>}
                                                </div>
                                            </div>
                                            <div className="asset right">
                                                <input type="file" id={`audio-upload-${this.props.node.getUuid()}-${idx}`} onChange={this.optionAudioFileSelected(idx)} />
                                                <div className="dropzone-asset audio-asset"
                                                     title={t('editor.diagram.stage.audio')}
                                                     onClick={this.showOptionAudioFileSelector(idx)}
                                                     onDrop={this.onDropOptionAudio(idx)}
                                                     onDragOver={event => { event.preventDefault(); }}>
                                                    {!this.props.node.getOptionAudio(idx) && <span className="dropzone glyphicon glyphicon-music"/>}
                                                    {this.props.node.getOptionAudio(idx) && <>
                                                        <div className="delete" title={t('editor.diagram.stage.resetAudio')} onClick={this.resetOptionAudio(idx)}/>
                                                        <span className="dropzone glyphicon glyphicon-play"/>
                                                    </>}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.optionsOut[idx]} className={`option-port ${this.getNodeError('optionsOut_'+idx) ? 'error' : ''}`}/>
                                </div>
                            )}
                            <div className="option">
                                <div className="policy">
                                    <input type="radio" name="menu-option-random" value="menu-option-random" checked={this.props.node.getDefaultOption() === -1} onChange={this.editDefaultOption(-1)} title={t('editor.diagram.menu.defaultOption')}/>
                                </div>
                                <div className="name-and-assets">
                                    <div className="option-name">
                                        <span>{t('editor.diagram.menu.random')}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                {this.props.node.fromPort && <StudioPortWidget engine={this.props.diagramEngine} model={this.props.node.fromPort} className={`from-port ${this.getNodeError('fromPort') ? 'error' : ''}`}/>}
            </div>
        );
    }

}

MenuNodeWidget.propTypes = {
    node: PropTypes.instanceOf(MenuNodeModel).isRequired,
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
    )(MenuNodeWidget)
)
