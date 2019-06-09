/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { toast } from 'react-toastify';

import {fetchDeviceInfos, fetchDevicePacks, addFromLibrary, removeFromDevice} from '../services/device';
import {fetchLibraryInfos, fetchLibraryPacks} from '../services/library';


export const actionLoadLibrary = () => {
    return dispatch => {
        let toastId = toast("Loading library...", { autoClose: false });
        return fetchLibraryInfos()
            .then(metadata => {
                console.log("fetching library packs...");
                toast.update(toastId,{ render: "Fetching library's story packs..." });
                return fetchLibraryPacks()
                    .then(packs => {
                        toast.update(toastId, { type: toast.TYPE.INFO, render: `Fetched ${packs.length} packs from library.`, autoClose: 5000 });
                        dispatch(setLibrary(metadata, packs));
                    })
                    .catch(e => {
                        console.error('failed to fetch library packs', e);
                        toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to fetch packs from library.`, autoClose: 5000 });
                    });
            })
            .catch(e => {
                console.error('failed to fetch library infos', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to load library.`, autoClose: 5000 });
            });
    }
};

export const setLibrary = (metadata, packs) => ({
    type: 'SET_LIBRARY',
    metadata,
    packs
});


export const actionCheckDevice = () => {
    return dispatch => {
        let toastId = toast("Checking device...", { autoClose: false });
        return fetchDeviceInfos()
            .then(metadata => {
                if (metadata && Object.keys(metadata).length > 0 && metadata.plugged) {
                    toast.update(toastId, { type: toast.TYPE.INFO, render: `Device is plugged.`, autoClose: 5000 });
                    dispatch(actionDevicePlugged(metadata));
                } else {
                    // Device not plugged, nothing to do
                    toast.dismiss(toastId);
                }
            })
            .catch(e => {
                console.error('failed to fetch device infos', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to check device.`, autoClose: 5000 });
            });
    }
};

export const actionDevicePlugged = (metadata) => {
    return dispatch => {
        dispatch(devicePlugged(metadata));

        console.log("fetching device packs...");
        let toastId = toast("Fetching device's story packs...", { autoClose: false });
        return fetchDevicePacks()
            .then(packs => {
                toast.update(toastId, { type: toast.TYPE.INFO, render: `Fetched ${packs.length} packs from device.`, autoClose: 5000 });
                dispatch(setDevicePacks(packs));
            })
            .catch(e => {
                console.error('failed to fetch device packs', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to fetch packs from device.`, autoClose: 5000 });
            });
    }
};

export const actionRefreshDevice = () => {
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
                toast("Failed to refresh device.", { type: toast.TYPE.ERROR, autoClose: 5000 });
            });
    }
};

export const actionAddFromLibrary = (path, context) => {
    return dispatch => {
        let toastId = toast("Adding pack to device...", { autoClose: false });
        return addFromLibrary(path)
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
                        toast.update(toastId, {progress: null, type: toast.TYPE.SUCCESS, render: 'Pack added to device', autoClose: 5000});
                        // Refresh device metadata and packs list
                        dispatch(actionRefreshDevice());
                    } else {
                        toast.update(toastId, {progress: null, type: toast.TYPE.ERROR, render: 'Failed to add pack to device', autoClose: 5000});
                    }
                });
            })
            .catch(e => {
                console.error('failed to add pack to device', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to add pack to device.`, autoClose: 5000 });
            });
    }
};

export const actionRemoveFromDevice = (uuid) => {
    return dispatch => {
        let toastId = toast("Removing pack from device...", { autoClose: false });
        return removeFromDevice(uuid)
            .then(resp => {
                if (resp.success) {
                    toast.update(toastId, {type: toast.TYPE.SUCCESS, render: 'Pack removed from device', autoClose: 5000});
                    // Refresh device metadata and packs list
                    dispatch(actionRefreshDevice());
                } else {
                    toast.update(toastId, {type: toast.TYPE.ERROR, render: 'Failed to remove pack from device', autoClose: 5000});
                }
            })
            .catch(e => {
                console.error('failed to remove pack from device', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to remove pack from device.`, autoClose: 5000 });
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
