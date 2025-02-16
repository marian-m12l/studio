import { toast } from 'react-toastify';
import { Mutex, withTimeout } from 'async-mutex';
import { fetchDeviceInfos, fetchDevicePacks, addFromLibrary, removeFromDevice, reorderPacks } from '../services/device';
import IssueReportToast from "../components/IssueReportToast";
import { useTranslation } from 'react-i18next';
import { DevicePackInfos } from '../../@types/pack';
import { FsDeviceInfos } from '../../@types/device';

const mutex = withTimeout(new Mutex(), 100);

export const devicePlugged = (metadata:FsDeviceInfos) => ({
    type: 'DEVICE_PLUGGED',
    metadata
});

export const deviceUnplugged = () => ({
    type: 'DEVICE_UNPLUGGED'
});

export const setDevicePacks = (packs:DevicePackInfos[]) => ({
    type: 'SET_DEVICE_PACKS',
    packs: packs
});

export const actionCheckDevice = () => {
    return dispatch => mutex.acquire()
        .then(
            release => {
                const { t } = useTranslation();

                const toastId = toast(t('toasts.device.checking'), { autoClose: false });
                return fetchDeviceInfos()
                    .then(metadata => {
                        if (metadata && Object.keys(metadata).length > 0 && metadata.plugged) {
                            toast.update(toastId, { type: "info", render: t('toasts.device.plugged'), autoClose: 5000 });
                            dispatch(actionDevicePlugged(metadata));
                        } else {
                            toast.dismiss(toastId);
                        }
                    })
                    .catch(e => {
                        console.error('failed to fetch device infos', e);
                        toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.device.checkingFailed')} error={e} />, autoClose: false });
                    })
                    .finally(() => {
                        release();
                    });
            },
            e => toast.error(t('toasts.device.busy')));
};

export const actionDevicePlugged = (metadata:FsDeviceInfos) => {
    return dispatch => mutex.acquire()
        .then(
            release => {
                const { t } = useTranslation();

                dispatch(devicePlugged(metadata));
                const toastId = toast(t('toasts.device.fetching'), { autoClose: false });
                return fetchDevicePacks()
                    .then(packs => {
                        toast.update(toastId, { type: "info", render: t('toasts.device.fetched', { count: packs.length }), autoClose: 5000 });
                        dispatch(setDevicePacks(packs));
                    })
                    .catch(e => {
                        console.error('failed to fetch device packs', e);
                        toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.device.fetchingFailed')} error={e} />, autoClose: false });
                    })
                    .finally(() => {
                        release();
                    });
            },
            () => {
                const { t } = useTranslation();

                toast.error(t('toasts.device.busy'));
            });
};

export const actionRefreshDevice = (t) => {
    return dispatch => mutex.acquire()
        .then(
            release => {
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
                        toast(<IssueReportToast content={t('toasts.device.refreshingFailed')} error={e} />, { type: "error", autoClose: false });
                    })
                    .finally(() => {
                        release();
                    });
            },
            e => {
                toast.error(t('toasts.device.busy'));
            });
};

export const actionAddFromLibrary = (uuid:string, path:string, format:string, driver, context) => {
    return dispatch => mutex.acquire()
        .then(
            release => {
                if (driver !== format) {
                    console.error('pack format is not compatible with the device');
                    toast.error(t('toasts.device.notCompatible'));
                    release();
                } else {
                    const toastId = toast(t('toasts.device.adding'), { autoClose: false });
                    return addFromLibrary(uuid, path)
                        .then(resp => {
                            const transferId = resp.transferId;
                            context.eventBus.registerHandler('storyteller.transfer.' + transferId + '.progress', (error, message) => {
                                if (message.body.progress < 1) {
                                    toast.update(toastId, { progress: message.body.progress, autoClose: false });
                                }
                            });
                            context.eventBus.registerHandler('storyteller.transfer.' + transferId + '.done', (error, message) => {
                                if (message.body.success) {
                                    toast.update(toastId, { progress: null, type: "success", render: t('toasts.device.added'), autoClose: 5000 });
                                    dispatch(actionRefreshDevice(t));
                                } else {
                                    toast.update(toastId, { progress: null, type: "error", render: <IssueReportToast content={t('toasts.device.addingFailed')} />, autoClose: false });
                                }
                                release();
                            });
                        })
                        .catch(e => {
                            console.error('failed to add pack to device', e);
                            toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.device.addingFailed')} error={e} />, autoClose: false });
                            release();
                        });
                }
            },
            e => {
                toast.error(t('toasts.device.busy'));
            });
};

export const actionRemoveFromDevice = (uuid, t) => {
    return dispatch => mutex.acquire()
        .then(
            release => {
                const toastId = toast(t('toasts.device.removing'), { autoClose: false });
                return removeFromDevice(uuid)
                    .then(resp => {
                        if (resp.success) {
                            toast.update(toastId, { type: "success", render: t('toasts.device.removed'), autoClose: 5000 });
                            dispatch(actionRefreshDevice(t));
                        } else {
                            toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.device.removingFailed')} />, autoClose: false });
                        }
                    })
                    .catch(e => {
                        console.error('failed to remove pack from device', e);
                        toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.device.removingFailed')} error={e} />, autoClose: false });
                    })
                    .finally(() => {
                        release();
                    });
            },
            e => {
                toast.error(t('toasts.device.busy'));
            });
};

export const actionReorderOnDevice = (uuids, t) => {
    return dispatch => mutex.acquire()
        .then(
            release => {
                const toastId = toast(t('toasts.device.reordering'), { autoClose: false });
                return reorderPacks(uuids)
                    .then(resp => {
                        if (resp.success) {
                            toast.update(toastId, { type: "success", render: t('toasts.device.reordered'), autoClose: 5000 });
                            dispatch(actionRefreshDevice(t));
                        } else {
                            toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.device.reorderingFailed')} />, autoClose: false });
                        }
                    })
                    .catch(e => {
                        console.error('failed to reorder packs on device', e);
                        toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.device.reorderingFailed')} error={e} />, autoClose: false });
                    })
                    .finally(() => {
                        release();
                    });
            },
            e => {
                toast.error(t('toasts.device.busy'));
            });
};