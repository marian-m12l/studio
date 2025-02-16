import { connect } from 'react-redux';
import {
    deviceUnplugged,
    setEditorDiagram,
    showLibrary,
    showEditor,
    setAnnounceOptOut,
    setAllowEnriched
} from "../actions";
import { actionCheckDevice, actionDevicePlugged } from '../actions/device.actions';
import { actionLoadEvergreen } from '../actions/evergreen.actions';
import { actionLoadLibrary } from '../actions/library.actions';

const mapStateToProps = (state) => ({
    evergreen: state.evergreen,
    settings: state.settings,
    ui: state.ui,
    viewer: state.viewer
});

const mapDispatchToProps = (dispatch) => ({
    checkDevice: () => dispatch(actionCheckDevice()),
    onDevicePlugged: (metadata) => dispatch(actionDevicePlugged(metadata)),
    onDeviceUnplugged: () => dispatch(deviceUnplugged()),
    loadLibrary: () => dispatch(actionLoadLibrary()),
    setEditorDiagram: (diagram, filename:string) => dispatch(setEditorDiagram(diagram, filename)),
    dispatchShowLibrary: () => dispatch(showLibrary()),
    dispatchShowEditor: () => dispatch(showEditor()),
    loadEvergreen: (announceOptOut) => dispatch(actionLoadEvergreen(announceOptOut)),
    setAnnounceOptOut: (announceOptOut) => dispatch(setAnnounceOptOut(announceOptOut)),
    setAllowEnriched: (allowEnriched) => dispatch(setAllowEnriched(allowEnriched))
});

export { mapStateToProps, mapDispatchToProps };