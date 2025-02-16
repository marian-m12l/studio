import { useState, useEffect } from 'react';
import { connect } from 'react-redux';
import { ToastContainer, toast } from 'react-toastify';
import EventBus from 'vertx3-eventbus-client';
import { EventBus as EventBusType } from 'vertx3-eventbus-client';
import { useTranslation } from 'react-i18next';
import { marked } from 'marked';
import 'react-toastify/dist/ReactToastify.css';
import Switch from "react-switch";
import { AppContext } from './AppContext';
import Modal from './components/Modal';
import PackEditor from './components/diagram/PackEditor';
import PackLibrary from './components/PackLibrary';
import EditorPackViewer from "./components/viewer/EditorPackViewer";
import { simplifiedSample } from "./utils/sample";
import { generateFilename } from "./utils/packs";
import { mapStateToProps, mapDispatchToProps } from './store/app';
import './App.css';
import {
    LOCAL_STORAGE_ANNOUNCE_LAST_SHOWN
} from "./utils/storage";

const App = (props) => {
    const [eventBus, setEventBus] = useState<EventBusType | null>(null);
    const [shown, setShown] = useState(null);
    const [announce, setAnnounce] = useState(null);
    const [showSettings, setShowSettings] = useState(false);
    const {t, i18n} = useTranslation();

    useEffect(() => {
        console.log("Setting up vert.x event bus...");
        const eb = new EventBus('http://localhost:8080/eventbus');
        setEventBus(eb);

        eb.onopen = () => {
            console.log("vert.x event bus open. Registering handlers...");
            eb.registerHandler('storyteller.plugged', (error: string, message: { body: string; }) => {
                console.log("Received `storyteller.plugged` event from vert.x event bus.");
                console.log(message.body);
                toast.info(t('toasts.device.monitoring.plugged'));
                props.onDevicePlugged(message.body);
            });
            eb.registerHandler('storyteller.unplugged', () => {
                console.log("Received `storyteller.unplugged` event from vert.x event bus.");
                toast.info(t('toasts.device.monitoring.unplugged'));
                props.onDeviceUnplugged();
            });
            eb.registerHandler('storyteller.failure', () => {
                console.log("Received `storyteller.failure` event from vert.x event bus.");
                toast.error(t('toasts.device.monitoring.failure'));
                props.onDeviceUnplugged();
            });
        };

        props.checkDevice();
        props.loadLibrary();
        props.loadEvergreen(props.settings.announceOptOut);

        const model = simplifiedSample();
        props.setEditorDiagram(model, generateFilename(model));

        props.dispatchShowLibrary();
    }, [props, t]);

    useEffect(() => {
        if (props.evergreen.announce !== null && props.evergreen.announce !== props.evergreen.announce) {
            const announceTime = Date.parse(props.evergreen.announce.date);
            const lastAnnounceShown = JSON.stringify(localStorage.getItem(String(LOCAL_STORAGE_ANNOUNCE_LAST_SHOWN)) || 0);
            if (announceTime > Number(lastAnnounceShown)) {
                setAnnounce(props.evergreen.announce.content);
            }
        }
        setShown(props.ui.shown);
    }, [props.evergreen.announce, props.ui.shown, props.viewer.show]);

    const showLibrary = () => {
        props.dispatchShowLibrary();
    };

    const showEditor = () => {
        props.dispatchShowEditor();
    };

    const dismissAnnounceDialog = () => {
        localStorage.setItem(String(LOCAL_STORAGE_ANNOUNCE_LAST_SHOWN), Date.now().toString());
        setAnnounce(null);
    };

    const announceOptOut = () => {
        props.setAnnounceOptOut(true);
        dismissAnnounceDialog();
    };

    const showSettingsDialog = () => {
        setShowSettings(true);
    };

    const dismissSettingsDialog = () => {
        setShowSettings(false);
    };

    const onAnnounceOptOutChanged = (announceOptOut: any) => {
        props.setAnnounceOptOut(announceOptOut);
    };

    const onAllowEnrichedChanged = (allowEnriched: any) => {
        props.setAllowEnriched(allowEnriched);
    };

    return (
        <AppContext.Provider value={{ eventBus }}>
            <div className="App">
                <ToastContainer />
                {announce && <Modal id={`announce-dialog`}
                    className="announce-dialog"
                    title={"\uD83E\uDD41 \uD83E\uDD41 \uD83E\uDD41"}
                    content={<div dangerouslySetInnerHTML={{ __html: marked(announce) }} ></div>}
                    buttons={[
                        { label: t('dialogs.announce.optout'), onClick: announceOptOut },
                        { label: t('dialogs.shared.ok'), onClick: dismissAnnounceDialog }
                    ]}
                    onClose={dismissAnnounceDialog}
                />}
                {showSettings && <Modal id={`settings-dialog`}
                    className="settings-dialog"
                    title={t('dialogs.settings.title')}
                    content={<div>
                        <div><span>{t('dialogs.settings.announceOptOut')}</span><Switch onChange={onAnnounceOptOutChanged} checked={props.settings.announceOptOut} height={15} width={35} handleDiameter={20} boxShadow="0px 1px 5px rgba(0, 0, 0, 0.6)" activeBoxShadow="0px 0px 1px 10px rgba(0, 0, 0, 0.2)" uncheckedIcon={false} checkedIcon={false} /></div>
                        <div><span>{t('dialogs.settings.allowEnriched')}</span><Switch onChange={onAllowEnrichedChanged} checked={props.settings.allowEnriched} height={15} width={35} handleDiameter={20} boxShadow="0px 1px 5px rgba(0, 0, 0, 0.6)" activeBoxShadow="0px 0px 1px 10px rgba(0, 0, 0, 0.2)" uncheckedIcon={false} checkedIcon={false} /></div>
                    </div>}
                    buttons={[
                        { label: t('dialogs.shared.ok'), onClick: dismissSettingsDialog }
                    ]}
                    onClose={dismissSettingsDialog}
                />}
                {props.viewer.show && <EditorPackViewer />}
                <header className="App-header">
                    <div className="flags">
                        <span title="Français" role="img" aria-label="FR" onClick={() => i18n.changeLanguage('fr')}>🇫🇷&nbsp;</span>
                        <span title="English" role="img" aria-label="GB" onClick={() => i18n.changeLanguage('en')}>🇬🇧&nbsp;</span>
                        <span title={t('header.buttons.settings')} className="btn glyphicon glyphicon-wrench" onClick={showSettingsDialog} />
                    </div>
                    <div className="welcome">
                        {t('header.welcome')}
                        {props.evergreen.version && <span className="version"> ({props.evergreen.version})</span>}
                    </div>
                    <div className="controls">
                        <span title={t('header.buttons.library')} className={`btn glyphicon glyphicon-film ${shown === 'library' && 'active'}`} onClick={showLibrary} />
                        <span title={t('header.buttons.editor')} className={`btn glyphicon glyphicon-edit ${shown === 'editor' && 'active'}`} onClick={showEditor} />
                    </div>
                </header>
                {shown === 'library' && <PackLibrary />}
                {shown === 'editor' && <PackEditor />}
            </div>
        </AppContext.Provider>
    );
};

export default connect(mapStateToProps, mapDispatchToProps)(App);