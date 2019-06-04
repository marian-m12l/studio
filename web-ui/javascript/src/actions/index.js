/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { toast } from 'react-toastify';

import {fetchDeviceInfos, fetchDevicePacks} from '../services/device';


export const setLibrary = (metadata, packs) => ({
    type: 'SET_LIBRARY',
    metadata,
    packs
});


export const checkDevice = () => {
    return dispatch => {
        let toastId = toast("Checking device...", { autoClose: false });
        return fetchDeviceInfos()
            .then(metadata => {
                if (metadata && Object.keys(metadata).length > 0) {
                    toast.update(toastId, {type: toast.TYPE.INFO, render: `Device is plugged.`, autoClose: 5000});
                    dispatch(devicePlugged(metadata));
                } else {
                    // Device not plugged, nothing to do
                }
            })
            .catch(e => {
                console.error('failed to fetch device infos', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to check device.`, autoClose: 5000 });
            });
    }
};

export const devicePlugged = (metadata) => {
    return dispatch => {
        dispatch({
            type: 'DEVICE_PLUGGED',
            metadata
        });

        console.log("fetching device packs...");
        let toastId = toast("Fetching device's story packs...", { autoClose: false });
        return fetchDevicePacks()
            .then(packs => {
                console.log('fetched packs: ' + packs);
                toast.update(toastId, { type: toast.TYPE.INFO, render: `Fetched ${packs.length} packs from device.`, autoClose: 5000 });
                dispatch(setDevicePacks(packs));
            })
            .catch(e => {
                console.error('failed to fetch device packs', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: `Failed to fetch packs from device.`, autoClose: 5000 });
            });
    }
};

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

export const setViewerStage = (stage) => ({
    type: 'SET_VIEWER_STAGE',
    stage
});

export const setViewerAction = (action) => ({
    type: 'SET_VIEWER_ACTION',
    action
});
