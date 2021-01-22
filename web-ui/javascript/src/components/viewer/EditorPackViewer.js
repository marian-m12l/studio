/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import {connect} from "react-redux";
import { toast } from 'react-toastify';
import {withTranslation} from "react-i18next";

import PackViewer from './PackViewer';
import {hideViewer, setViewerAction, setViewerStage, setViewerOptions} from "../../actions";

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
                image={props.viewer.stage.getImage()}
                audio={props.viewer.stage.getAudio()}
                wheelClickedLeft={this.onWheelLeft}
                wheelClickedRight={this.onWheelRight}
                homeClicked={this.onHome}
                pauseClicked={this.onPause}
                okClicked={this.onOk}
                closeClicked={() => { this.onClose(); props.hideViewer(); }}
                controls={props.viewer.stage.getControls()}
                options={props.viewer.options}
                optionToggled={(optionName) => { this.onOptionToggled(optionName); }}
            />
        });
    };

    onWheelLeft = () => {
        const { t } = this.props;
        if (this.props.viewer.action.node) {
            const [stage, action] = this.props.viewer.action.node.onWheelLeft(this.props.viewer.action.index, this.props.viewer.diagram);
            if (stage && action) {
                this.props.setViewerStage(stage);
                this.props.setViewerAction(action);
            } else {
                toast.error(t('toasts.viewer.missingPrev'));
            }
        }
    };

    onWheelRight = () => {
        const { t } = this.props;
        if (this.props.viewer.action.node) {
            const [stage, action] = this.props.viewer.action.node.onWheelRight(this.props.viewer.action.index, this.props.viewer.diagram);
            if (stage && action) {
                this.props.setViewerStage(stage);
                this.props.setViewerAction(action);
            } else {
                toast.error(t('toasts.viewer.missingNext'));
            }
        }
    };

    onOk = () => {
        const { t } = this.props;
        const [stage, action] = this.props.viewer.stage.onOk(this.props.viewer.diagram);
        if (stage && action) {
            if (stage === this.props.viewer.stage) {
                toast.error(t('toasts.viewer.invalidOk'));
            } else {
                this.props.setViewerStage(stage);
                this.props.setViewerAction(action);
            }
        } else {
            toast.error(t('toasts.viewer.missingOk'));
        }
    };

    onHome = () => {
        const { t } = this.props;
        const [stage, action] = this.props.viewer.stage.onHome(this.props.viewer.diagram);
        if (stage && action) {
            if (stage === this.props.viewer.stage) {
                toast.error(t('toasts.viewer.invalidHome'));
            } else {
                this.props.setViewerStage(stage);
                this.props.setViewerAction(action);
            }
        } else {
            toast.error(t('toasts.viewer.missingHome'));
        }
    };

    onPause = () => {
        // no-op
    };

    onClose = () => {
        this.props.hideViewer();
    };

    onOptionToggled = (optionName) => {
        let options = this.props.viewer.options;
        options[optionName] = !options[optionName];
        this.props.setViewerOptions(options);
    };

    render() {
        return (
            <>
                <div className={`editor-viewer-content editor-viewer-center ${this.props.viewer.options.translucent && 'translucent'}`}>
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
    setViewerAction: (action) => dispatch(setViewerAction(action)),
    setViewerOptions: (options) => dispatch(setViewerOptions(options))
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(EditorPackViewer)
)
