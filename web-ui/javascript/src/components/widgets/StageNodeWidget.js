/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import * as SRD from 'storm-react-diagrams';

import EditableHeader from './composites/EditableHeader';
import StageNodeModel from "../models/StageNodeModel";


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
    };

    toggleControl = (control) => {
        return () => {
            this.props.node.toggleControl(control);
            this.props.updateCanvas();
        };
    };

    editImage = (result) => {
        this.props.node.image = result;
        this.props.updateCanvas();
    };

    editAudio = (result) => {
        this.props.node.audio = result;
        this.props.updateCanvas();
    };

    render() {
        return (
            <div className='basic-node stage-node'>
                <EditableHeader beingEdited={this.state.beingEdited} onToggleEdit={this.toggleEdit} onChange={this.editName} node={this.props.node} />
                <div className="controls">
                    <span title="Allow wheel selection" className={'btn btn-xs glyphicon glyphicon-resize-horizontal' + (this.props.node.controls.wheel ? ' active' : '')} onClick={this.toggleControl('wheel')}/>
                    <span title="Allow OK button" className={'btn btn-xs glyphicon glyphicon-ok' + (this.props.node.controls.ok ? ' active' : '')} onClick={this.toggleControl('ok')}/>
                    <span title="Allow HOME button" className={'btn btn-xs glyphicon glyphicon-home' + (this.props.node.controls.home ? ' active' : '')} onClick={this.toggleControl('home')}/>
                    <span title="Allow PAUSE button" className={'btn btn-xs glyphicon glyphicon-pause' + (this.props.node.controls.pause ? ' active' : '')} onClick={this.toggleControl('pause')}/>
                    <span title="Enable autoplay" className={'btn btn-xs glyphicon glyphicon-play' + (this.props.node.controls.autoplay ? ' active' : '')} onClick={this.toggleControl('autoplay')}/>
                </div>
                <div className="assets">
                    <div className="image-asset"
                        onDrop={event => {
                            event.preventDefault();
                            if (!event.dataTransfer.items && !event.dataTransfer.files) {
                                // Ignore data transfer (e.g. node drop)
                                return;
                            }
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
                            console.log(file.type);
                            if (file.type !== 'image/bmp') {
                                // TODO error notification
                                return;
                            }
                            let reader = new FileReader();
                            let that = this;
                            reader.addEventListener("load", function () {
                                that.editImage(reader.result);
                            }, false);
                            reader.readAsDataURL(file);
                        }}
                        onDragOver={event => {
                            event.preventDefault();
                        }}>
                        {!this.props.node.image && <span className="dropzone glyphicon glyphicon-picture"/>}
                        {this.props.node.image && <img src={this.props.node.image} className="dropzone" style={{height: '43px'}}/>}
                    </div>
                    <div className="audio-asset"
                        onDrop={event => {
                            event.preventDefault();
                            if (!event.dataTransfer.items && !event.dataTransfer.files) {
                                // Ignore data transfer (e.g. node drop)
                                return;
                            }
                            let file = null;
                            if (event.dataTransfer.items) {
                                // Use first file only
                                // If dropped items aren't files, reject them
                                if (event.dataTransfer.items[0].kind === 'file') {
                                    file = event.dataTransfer.items[0].getAsFile();
                                    console.log('Dropped item file name = ' + file.name);
                                }
                            } else {
                                // Use first file only
                                file = event.dataTransfer.files[0];
                                console.log('Dropped file name = ' + file.name);
                            }
                            console.log(file.type);
                            if (file.type !== 'audio/x-wav') {
                                // TODO error notification
                                return;
                            }
                            let reader = new FileReader();
                            let that = this;
                            reader.addEventListener("load", function () {
                                that.editAudio(reader.result);
                            }, false);
                            reader.readAsDataURL(file);
                        }}
                        onDragOver={event => {
                            event.preventDefault();
                        }}>
                        {!this.props.node.audio && <span className="dropzone glyphicon glyphicon-music"/>}
                        {this.props.node.audio && <span className="dropzone glyphicon glyphicon-play" onClick={e => {
                            new Audio(this.props.node.audio).play();
                        }}/>}
                    </div>
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

export default StageNodeWidget;
