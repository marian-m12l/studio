/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import {connect} from "react-redux";
import { toast } from 'react-toastify';

import PackViewer from './PackViewer';
import {hideViewer, setViewerAction, setViewerStage} from "../../actions";

import './EditorPackViewer.css';


class EditorPackViewer extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            viewer: null
        };
    }

    componentDidMount() {
        this.setViewer(this.props);
    };

    componentWillReceiveProps(nextProps, nextContext) {
        this.setViewer(nextProps);
    };

    setViewer = (props) => {
        this.setState({
            viewer: <PackViewer
                image={props.viewer.stage.image}
                audio={props.viewer.stage.audio}
                wheelClickedLeft={this.onWheelLeft}
                wheelClickedRight={this.onWheelRight}
                homeClicked={this.onHome}
                pauseClicked={this.onPause}
                okClicked={this.onOk}
                closeClicked={() => { this.onClose(); props.hideViewer(); }}
                controls={props.viewer.stage.controls}
            />
        });
    };

    onWheelLeft = () => {
        if (this.props.viewer.action.node) {
            let nextIndex = this.props.viewer.action.index === 0 ? (this.props.viewer.action.node.optionsOut.length - 1) : (this.props.viewer.action.index - 1);
            let optionLinks = Object.values(this.props.viewer.action.node.optionsOut[nextIndex].getLinks());
            if (optionLinks.length !== 1) {
                toast.error('Missing stage node for previous option');
            } else {
                let nextChoice = optionLinks[0].getTargetPort().getParent();

                this.props.setViewerStage(nextChoice);
                this.props.setViewerAction({
                    node: this.props.viewer.action.node,
                    index: nextIndex
                });
            }
        }
    };

    onWheelRight = () => {
        if (this.props.viewer.action.node) {
            let nextIndex = (this.props.viewer.action.index + 1) % this.props.viewer.action.node.optionsOut.length;
            let optionLinks = Object.values(this.props.viewer.action.node.optionsOut[nextIndex].getLinks());
            if (optionLinks.length !== 1) {
                toast.error('Missing stage node for next option');
            } else {
                let nextChoice = optionLinks[0].getTargetPort().getParent();

                this.props.setViewerStage(nextChoice);
                this.props.setViewerAction({
                    node: this.props.viewer.action.node,
                    index: nextIndex
                });
            }
        }
    };

    onOk = () => {
        if (this.props.viewer.stage.okPort) {
            let okLinks = Object.values(this.props.viewer.stage.okPort.getLinks());
            if (okLinks.length !== 1) {
                toast.error('Missing action node for OK transition');
            } else {
                let okTargetPort = okLinks[0].getTargetPort();
                let okTargetActionNode = okTargetPort.getParent();
                let targetIndex = okTargetActionNode.optionsIn.indexOf(okTargetPort);
                let optionLinks = Object.values(okTargetActionNode.optionsOut[targetIndex].getLinks());
                if (optionLinks.length !== 1) {
                    toast.error('Missing stage node for OK transition');
                } else {
                    let nextNode = optionLinks[0].getTargetPort().getParent();

                    this.props.setViewerStage(nextNode);
                    this.props.setViewerAction({
                        node: okTargetActionNode,
                        index: targetIndex
                    });
                }
            }
        }
    };

    onHome = () => {
        if (this.props.viewer.stage.homePort) {
            let homeLinks = Object.values(this.props.viewer.stage.homePort.getLinks());
            if (homeLinks.length !== 1) {
                // Back to main (pack selection) stage node, which is (theoretically) the only stage node with an empty from port
                let mainNode = Object.values(this.props.viewer.diagram.nodes)
                    .filter(node => node.getType() === 'stage')
                    .filter(node => Object.values(node.fromPort.getLinks()).length === 0)[0];
                this.props.setViewerStage(mainNode);
                this.props.setViewerAction({
                    node: null,
                    index: null
                });
            } else {
                let homeTargetPort = homeLinks[0].getTargetPort();
                let homeTargetActionNode = homeTargetPort.getParent();
                let targetIndex = homeTargetActionNode.optionsIn.indexOf(homeTargetPort);
                let optionLinks = Object.values(homeTargetActionNode.optionsOut[targetIndex].getLinks());
                if (optionLinks.length !== 1) {
                    toast.error('Missing stage node for HOME transition');
                } else {
                    let nextNode = optionLinks[0].getTargetPort().getParent();

                    this.props.setViewerStage(nextNode);
                    this.props.setViewerAction({
                        node: homeTargetActionNode,
                        index: targetIndex
                    });
                }
            }
        }
    };

    onPause = () => {
        // no-op
    };

    onClose = () => {
        this.props.hideViewer();
    };

    render() {
        return (
            <>
                <div className="editor-viewer-overlay" />
                <div className="editor-viewer-content editor-viewer-center">
                    {this.state.viewer}
                </div>
            </>
        );
    }
}

const mapStateToProps = (state, ownProps) => ({
    viewer: state.viewer
});

const mapDispatchToProps = (dispatch, ownProps) => ({
    hideViewer: () => dispatch(hideViewer()),
    setViewerStage: (stage) => dispatch(setViewerStage(stage)),
    setViewerAction: (action) => dispatch(setViewerAction(action))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(EditorPackViewer)
