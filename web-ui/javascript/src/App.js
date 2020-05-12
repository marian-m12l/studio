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
import marked from 'marked';
import 'react-toastify/dist/ReactToastify.css';

import {AppContext} from './AppContext';
import Modal from './components/Modal';
import PackEditor from './components/diagram/PackEditor';
import PackLibrary from './components/PackLibrary';
import EditorPackViewer from "./components/viewer/EditorPackViewer";
import {simplifiedSample} from "./utils/sample";
import {
    actionCheckDevice,
    actionDevicePlugged,
    deviceUnplugged,
    actionLoadLibrary,
    setEditorDiagram,
    showLibrary,
    showEditor,
    actionLoadEvergreen,
    actionLoadWatchdog
} from "./actions";

import './App.css';
import {generateFilename} from "./utils/packs";


class App extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            eventBus: null,
            shown: null,
            viewer: null,
            announce: null
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

            // Load evergeen infos on startup
            this.props.loadEvergreen();

            // Load watchdog infos on startup
            this.props.loadWatchdog();

            // Load sample diagram in editor
            let model = simplifiedSample();
            this.props.setEditorDiagram(model, generateFilename(model));

            this.props.dispatchShowLibrary();
        });
    }

    componentWillReceiveProps(nextProps, nextContext) {
        if (nextProps.evergreen.announce !== this.props.evergreen.announce && nextProps.evergreen.announce !== null) {
            // Check last announce display time in local storage and compare to announce time
            let announceTime = Date.parse(nextProps.evergreen.announce.date);
            let lastAnnounceShown = localStorage.getItem('lastAnnounceShown') || 0;
            console.log('announce: ' + announceTime);
            console.log('last shown: ' + lastAnnounceShown);
            if (announceTime > lastAnnounceShown) {
                console.log('Announce must be displayed');
                this.setState({announce: nextProps.evergreen.announce.content})
            } else {
                console.log('Announce already displayed');
            }
        }
        this.setState({
            shown: nextProps.ui.shown,
            viewer: nextProps.viewer.show ? <EditorPackViewer/> : null
        });
    }

    showLibrary = () => {
        this.props.dispatchShowLibrary();
    };

    showEditor = () => {
        this.props.dispatchShowEditor();
    };

    dismissAnnounceDialog = () => {
        localStorage.setItem('lastAnnounceShown', Date.now());
        this.setState({announce: null});
    };

    announceOptOut = () => {
        localStorage.setItem('announceOptOut', 1);
        this.dismissAnnounceDialog();
    };

    render() {
        const { t, i18n } = this.props;
        return (
            <AppContext.Provider value={{eventBus: this.state.eventBus}}>
                <div className="App">
                    <ToastContainer/>
                    {this.state.announce && <Modal id={`announce-dialog`}
                                                   className="announce-dialog"
                                                   title={"\uD83E\uDD41 \uD83E\uDD41 \uD83E\uDD41"}
                                                   content={<div dangerouslySetInnerHTML={{__html: marked(this.state.announce)}} ></div>}
                                                   buttons={[
                                                       { label: t('dialogs.announce.optout'), onClick: this.announceOptOut },
                                                       { label: t('dialogs.shared.ok'), onClick: this.dismissAnnounceDialog }
                                                   ]}
                                                   onClose={this.dismissAnnounceDialog}
                    />}
                    {this.state.viewer}
                    <header className="App-header">
                        <div className="flags">
                            <span title="Français" onClick={() => i18n.changeLanguage('fr')}>🇫🇷&nbsp;</span>
                            <span title="English" onClick={() => i18n.changeLanguage('en')}>🇬🇧&nbsp;</span>
                        </div>
                        <div  className="welcome">
                            {t('header.welcome')}
                            {this.props.evergreen.version && <span className="version"> ({this.props.evergreen.version})</span>}
                        </div>
                        <div className="controls">
                            <span title={t('header.buttons.library')} className={`btn glyphicon glyphicon-film ${this.state.shown === 'library' && 'active'}`} onClick={this.showLibrary}/>
                            <span title={t('header.buttons.editor')} className={`btn glyphicon glyphicon-edit ${this.state.shown === 'editor' && 'active'}`} onClick={this.showEditor}/>
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
    evergreen: state.evergreen,
    ui: state.ui,
    viewer: state.viewer
});

const mapDispatchToProps = (dispatch, ownProps) => ({
    checkDevice: () => dispatch(actionCheckDevice(ownProps.t)),
    onDevicePlugged: (metadata) => dispatch(actionDevicePlugged(metadata, ownProps.t)),
    onDeviceUnplugged: () => dispatch(deviceUnplugged()),
    loadLibrary: () => dispatch(actionLoadLibrary(ownProps.t)),
    setEditorDiagram: (diagram, filename) => dispatch(setEditorDiagram(diagram, filename)),
    dispatchShowLibrary: () => dispatch(showLibrary()),
    dispatchShowEditor: () => dispatch(showEditor()),
    loadEvergreen: () => dispatch(actionLoadEvergreen(ownProps.t)),
    loadWatchdog: () => dispatch(actionLoadWatchdog(ownProps.t))
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(App)
)
