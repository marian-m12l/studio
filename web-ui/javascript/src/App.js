/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { connect } from 'react-redux';
import { ToastContainer, toast } from 'react-toastify';
import EventBus from 'vertx3-eventbus-client';
import { withTranslation } from 'react-i18next';
import 'react-toastify/dist/ReactToastify.css';

import {AppContext} from './AppContext';
import PackEditor from './components/diagram/PackEditor';
import PackLibrary from './components/PackLibrary';
import EditorPackViewer from "./components/viewer/EditorPackViewer";
import {sample} from "./utils/sample";
import {actionCheckDevice, actionDevicePlugged, deviceUnplugged, actionLoadLibrary, setEditorDiagram} from "./actions";

import './App.css';


class App extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            eventBus: null,
            shown: 'library',
            viewer: null
        };
    }

    componentDidMount() {
        const { t } = this.props;
        // Set up vert.x eventbus
        console.log("Setting up vert.x event bus...");
        let eventBus = new EventBus('http://localhost:8080/eventbus');
        this.setState({eventBus}, () => {
            this.state.eventBus.onopen = () => {
                console.log("vert.x event bus open. Registering handlers...");
                this.state.eventBus.registerHandler('storyteller.plugged', (error, message) => {
                    console.log("Received `storyteller.plugged` event from vert.x event bus.");
                    console.log(message.body);
                    toast.info(t('toasts.device.monitoring.plugged'));
                    this.props.onDevicePlugged(message.body);
                });
                this.state.eventBus.registerHandler('storyteller.unplugged', (error, message) => {
                    console.log("Received `storyteller.unplugged` event from vert.x event bus.");
                    toast.info(t('toasts.device.monitoring.unplugged'));
                    this.props.onDeviceUnplugged();
                });
                this.state.eventBus.registerHandler('storyteller.failure', (error, message) => {
                    console.log("Received `storyteller.failure` event from vert.x event bus.");
                    toast.error(t('toasts.device.monitoring.failure'));
                    this.props.onDeviceUnplugged();
                });
            };

            // Check whether device is already plugged on startup
            this.props.checkDevice();

            // Load library on startup
            this.props.loadLibrary();

            // Load sample diagram in editor
            this.props.setEditorDiagram(sample());
        });
    }

    componentWillReceiveProps(nextProps, nextContext) {
        this.setState({
            viewer: nextProps.viewer.show ? <EditorPackViewer/> : null
        });
    }

    showEditor = () => {
        this.setState({
            shown: 'editor'
        });
    };

    showLibrary = () => {
        this.setState({
            shown: 'library'
        });
    };

    render() {
        const { t, i18n } = this.props;
        return (
            <AppContext.Provider value={{eventBus: this.state.eventBus}}>
                <div className="App">
                    <ToastContainer/>
                    {this.state.viewer}
                    <header className="App-header">
                        <div className="flags">
                            <span title="Français" onClick={() => i18n.changeLanguage('fr')}>🇫🇷&nbsp;</span>
                            <span title="English" onClick={() => i18n.changeLanguage('en')}>🇬🇧&nbsp;</span>
                        </div>
                        <div  className="welcome">
                            {t('header.welcome')}
                        </div>
                        <div className="controls">
                            <span title={t('header.buttons.library')} className={`btn glyphicon glyphicon-film ${this.state.shown === 'library' && 'active'}`} onClick={this.showLibrary}/>
                            <span title={t('header.buttons.editor')} className={`btn glyphicon glyphicon-wrench ${this.state.shown === 'editor' && 'active'}`} onClick={this.showEditor}/>
                        </div>
                    </header>
                    {this.state.shown === 'library' && <PackLibrary/>}
                    {this.state.shown === 'editor' && <PackEditor/>}
                </div>
            </AppContext.Provider>
        );
    }
}

const mapStateToProps = (state, ownProps) => ({
    viewer: state.viewer
});

const mapDispatchToProps = (dispatch, ownProps) => ({
    checkDevice: () => dispatch(actionCheckDevice(ownProps.t)),
    onDevicePlugged: (metadata) => dispatch(actionDevicePlugged(metadata, ownProps.t)),
    onDeviceUnplugged: () => dispatch(deviceUnplugged()),
    loadLibrary: () => dispatch(actionLoadLibrary(ownProps.t)),
    setEditorDiagram: (diagram) => dispatch(setEditorDiagram(diagram))
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(App)
)
