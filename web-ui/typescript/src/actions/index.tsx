/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { toast } from 'react-toastify';
import { Mutex, withTimeout } from 'async-mutex';

import IssueReportToast from "../components/IssueReportToast";
import { addToLibrary } from '../services/device';
import { fetchLibraryInfos, fetchLibraryPacks, } from '../services/library';
import { sortPacks } from "../utils/packs";
import { LibraryPack } from '../../@types/pack';
import { useTranslation } from 'react-i18next';


const mutex = withTimeout(new Mutex(), 100);


export const setLibrary = (metadata, packs: LibraryPack[]) => ({
    type: 'SET_LIBRARY',
    metadata,
    packs: sortPacks(packs)
});

export const actionAddToLibrary = (uuid, driver, context) => {
    return dispatch => mutex.acquire()
        .then(
            release => {
                const { t } = useTranslation();
                const toastId = toast(t('toasts.library.adding'), { autoClose: false });
                return addToLibrary(uuid, driver)
                    .then(resp => {
                        // Monitor transfer progress
                        const transferId = resp.transferId;
                        context.eventBus.registerHandler('storyteller.transfer.' + transferId + '.progress', (error, message: string) => {
                            console.log("Received `storyteller.transfer." + transferId + ".progress` event from vert.x event bus.");
                            console.log(message.body);
                            if (message.body.progress < 1) {
                                toast.update(toastId, { progress: message.body.progress, autoClose: false });
                            }
                        });
                        context.eventBus.registerHandler('storyteller.transfer.' + transferId + '.done', (error, message: string) => {
                            console.log("Received `storyteller.transfer." + transferId + ".done` event from vert.x event bus.");
                            console.log(message.body);
                            if (message.body.success) {
                                toast.update(toastId, { progress: null, type: "success", render: t('toasts.library.added'), autoClose: 5000 });
                                // Refresh device metadata and packs list
                                dispatch(actionRefreshLibrary(t));
                            } else {
                                toast.update(
                                    toastId, {
                                        progress: null, type: "error", render: <IssueReportToast content={t('toasts.library.addingFailed')} />, autoClose: false
                                });
                            }
                            // Always release the mutex
                            release();
                        });
                    })
                    .catch(e => {
                        console.error('failed to add pack to library', e);
                        toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.library.addingFailed')} error={e} />, autoClose: false });
                        // Always release the mutex
                        release();
                    });
            },
            e => {
                // Device is busy
                toast.error(t('toasts.device.busy'));
            });
};

export const actionRefreshLibrary = () => {
    return dispatch => {
        return fetchLibraryInfos()
            .then(metadata => {
                return fetchLibraryPacks()
                    .then(packs => {
                        dispatch(setLibrary(metadata, packs));
                    });
            })
            .catch(e => {
                const { t } = useTranslation();
                console.error('failed to refresh library', e);
                toast(<IssueReportToast content={t('toasts.library.refreshingFailed')} error={e} />, { type: "error", autoClose: false });
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
    packs: packs
});

export const showViewer = () => ({
    type: 'SHOW_VIEWER'
});

export const hideViewer = () => ({
    type: 'HIDE_VIEWER'
});



export const setEditorDiagram = (diagram, filename = null) => ({
    type: 'SET_EDITOR_DIAGRAM',
    diagram,
    filename
});

export const setEditorFilename = (filename = null) => ({
    type: 'SET_EDITOR_FILENAME',
    filename
});

export const setDiagramErrors = (errors: Error[]) => ({
    type: 'SET_DIAGRAM_ERRORS',
    errors
});

export const showLibrary = () => ({
    type: 'SHOW_LIBRARY'
});

export const showEditor = () => ({
    type: 'SHOW_EDITOR'
});

export const setApplicationVersion = (version: string) => ({
    type: 'SET_APPLICATION_VERSION',
    version
});

export const setAnnounce = (announce: string) => ({
    type: 'SET_ANNOUNCE',
    announce
});

export const setAnnounceOptOut = (announceOptOut: string) => ({
    type: 'SET_ANNOUNCE_OPTOUT',
    announceOptOut
});

export const setAllowEnriched = (allowEnriched: boolean) => ({
    type: 'SET_ALLOW_ENRICHED',
    allowEnriched
});
