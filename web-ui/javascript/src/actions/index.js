/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { toast } from 'react-toastify';

import {fetchDeviceInfos, fetchDevicePacks, addFromLibrary, removeFromDevice, addToLibrary} from '../services/device';
import {fetchLibraryInfos, fetchLibraryPacks} from '../services/library';


export const actionLoadLibrary = (t) => {
    return dispatch => {
        let toastId = toast(t('toasts.library.loading'), { autoClose: false });
        return fetchLibraryInfos()
            .then(metadata => {
                console.log("fetching library packs...");
                toast.update(toastId,{ render: t('toasts.library.fetching') });
                return fetchLibraryPacks()
                    .then(packs => {
                        toast.update(toastId, { type: toast.TYPE.INFO, render: t('toasts.library.fetched', { count: packs.length }), autoClose: 5000 });
                        dispatch(setLibrary(metadata, packs));
                    })
                    .catch(e => {
                        console.error('failed to fetch library packs', e);
                        toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.library.fetchingFailed'), autoClose: 5000 });
                    });
            })
            .catch(e => {
                console.error('failed to fetch library infos', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.library.loadingFailed'), autoClose: 5000 });
            });
    }
};

export const setLibrary = (metadata, packs) => ({
    type: 'SET_LIBRARY',
    metadata,
    packs
});


export const actionCheckDevice = (t) => {
    return dispatch => {
        let toastId = toast(t('toasts.device.checking'), { autoClose: false });
        return fetchDeviceInfos()
            .then(metadata => {
                if (metadata && Object.keys(metadata).length > 0 && metadata.plugged) {
                    toast.update(toastId, { type: toast.TYPE.INFO, render: t('toasts.device.plugged'), autoClose: 5000 });
                    dispatch(actionDevicePlugged(metadata, t));
                } else {
                    // Device not plugged, nothing to do
                    toast.dismiss(toastId);
                }
            })
            .catch(e => {
                console.error('failed to fetch device infos', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.device.checkingFailed'), autoClose: 5000 });
            });
    }
};

export const actionDevicePlugged = (metadata, t) => {
    return dispatch => {
        dispatch(devicePlugged(metadata));

        console.log("fetching device packs...");
        let toastId = toast(t('toasts.device.fetching'), { autoClose: false });
        return fetchDevicePacks()
            .then(packs => {
                toast.update(toastId, { type: toast.TYPE.INFO, render: t('toasts.device.fetched', { count: packs.length }), autoClose: 5000 });
                dispatch(setDevicePacks(packs));
            })
            .catch(e => {
                console.error('failed to fetch device packs', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.device.fetchingFailed'), autoClose: 5000 });
            });
    }
};

export const actionRefreshDevice = (t) => {
    return dispatch => {
        return fetchDeviceInfos()
            .then(metadata => {
                if (metadata && Object.keys(metadata).length > 0 && metadata.plugged) {
                    return fetchDevicePacks()
                        .then(packs => {
                            dispatch(devicePlugged(metadata));
                            dispatch(setDevicePacks(packs));
                        });
                }
            })
            .catch(e => {
                console.error('failed to refresh device', e);
                toast(t('toasts.device.refreshingFailed'), { type: toast.TYPE.ERROR, autoClose: 5000 });
            });
    }
};

export const actionAddFromLibrary = (uuid, path, context, t) => {
    return dispatch => {
        let toastId = toast(t('toasts.device.adding'), { autoClose: false });
        return addFromLibrary(uuid, path)
            .then(resp => {
                // Monitor transfer progress
                let transferId = resp.transferId;
                context.eventBus.registerHandler('storyteller.transfer.'+transferId+'.progress', (error, message) => {
                    console.log("Received `storyteller.transfer."+transferId+".progress` event from vert.x event bus.");
                    console.log(message.body);
                    toast.update(toastId, {progress: message.body.progress, autoClose: false});
                });
                context.eventBus.registerHandler('storyteller.transfer.'+transferId+'.done', (error, message) => {
                    console.log("Received `storyteller.transfer."+transferId+".done` event from vert.x event bus.");
                    console.log(message.body);
                    if (message.body.success) {
                        toast.update(toastId, {progress: null, type: toast.TYPE.SUCCESS, render: t('toasts.device.added'), autoClose: 5000});
                        // Refresh device metadata and packs list
                        dispatch(actionRefreshDevice(t));
                    } else {
                        toast.update(toastId, {progress: null, type: toast.TYPE.ERROR, render: t('toasts.device.addingFailed'), autoClose: 5000});
                    }
                });
            })
            .catch(e => {
                console.error('failed to add pack to device', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.device.addingFailed'), autoClose: 5000 });
            });
    }
};

export const actionRemoveFromDevice = (uuid, t) => {
    return dispatch => {
        let toastId = toast(t('toasts.device.removing'), { autoClose: false });
        return removeFromDevice(uuid)
            .then(resp => {
                if (resp.success) {
                    toast.update(toastId, {type: toast.TYPE.SUCCESS, render: t('toasts.device.removed'), autoClose: 5000});
                    // Refresh device metadata and packs list
                    dispatch(actionRefreshDevice(t));
                } else {
                    toast.update(toastId, {type: toast.TYPE.ERROR, render: t('toasts.device.removingFailed'), autoClose: 5000});
                }
            })
            .catch(e => {
                console.error('failed to remove pack from device', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.device.removingFailed'), autoClose: 5000 });
            });
    }
};

export const actionAddToLibrary = (uuid, context, t) => {
    return dispatch => {
        let toastId = toast(t('toasts.library.adding'), { autoClose: false });
        return addToLibrary(uuid)
            .then(resp => {
                // Monitor transfer progress
                let transferId = resp.transferId;
                context.eventBus.registerHandler('storyteller.transfer.'+transferId+'.progress', (error, message) => {
                    console.log("Received `storyteller.transfer."+transferId+".progress` event from vert.x event bus.");
                    console.log(message.body);
                    toast.update(toastId, {progress: message.body.progress, autoClose: false});
                });
                context.eventBus.registerHandler('storyteller.transfer.'+transferId+'.done', (error, message) => {
                    console.log("Received `storyteller.transfer."+transferId+".done` event from vert.x event bus.");
                    console.log(message.body);
                    if (message.body.success) {
                        toast.update(toastId, {progress: null, type: toast.TYPE.SUCCESS, render: t('toasts.library.added'), autoClose: 5000});
                        // Refresh device metadata and packs list
                        dispatch(actionRefreshLibrary(t));
                    } else {
                        toast.update(toastId, {progress: null, type: toast.TYPE.ERROR, render: t('toasts.library.addingFailed'), autoClose: 5000});
                    }
                });
            })
            .catch(e => {
                console.error('failed to add pack to library', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.library.addingFailed'), autoClose: 5000 });
            });
    }
};

export const actionRefreshLibrary = (t) => {
    return dispatch => {
        return fetchLibraryInfos()
            .then(metadata => {
                return fetchLibraryPacks()
                    .then(packs => {
                        dispatch(setLibrary(metadata, packs));
                    });
            })
            .catch(e => {
                console.error('failed to refresh library', e);
                toast(t('toasts.library.refreshingFailed'), { type: toast.TYPE.ERROR, autoClose: 5000 });
            });
    }
};

export const devicePlugged = (metadata) => ({
    type: 'DEVICE_PLUGGED',
    metadata
});

export const deviceUnplugged = () => ({
    type: 'DEVICE_UNPLUGGED'
});

export const setDevicePacks = (packs) => ({
    type: 'SET_DEVICE_PACKS',
    packs
});

export const showViewer = () => ({
    type: 'SHOW_VIEWER'
});

export const hideViewer = () => ({
    type: 'HIDE_VIEWER'
});

export const setViewerDiagram = (diagram) => ({
    type: 'SET_VIEWER_DIAGRAM',
    diagram
});

export const setViewerStage = (stage) => ({
    type: 'SET_VIEWER_STAGE',
    stage
});

export const setViewerAction = (action) => ({
    type: 'SET_VIEWER_ACTION',
    action
});

export const setEditorDiagram = (diagram) => ({
    type: 'SET_EDITOR_DIAGRAM',
    diagram
});
