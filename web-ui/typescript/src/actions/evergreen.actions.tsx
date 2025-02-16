import { toast } from 'react-toastify';
import { fetchEvergreenInfos, fetchEvergreenLatestRelease, fetchEvergreenAnnounce } from '../services/evergreen';
import IssueReportToast from "../components/IssueReportToast";
import { setApplicationVersion, setAnnounce } from './ui.actions';
import { useTranslation } from 'react-i18next';

export const actionLoadEvergreen = (announceOptOut) => {
    return dispatch => {
        const { t } = useTranslation();
        const toastId = toast(t('toasts.evergreen.loading'), { autoClose: false });
        return fetchEvergreenInfos()
            .then(infos => {
                dispatch(setApplicationVersion(infos.version));
                toast.update(toastId, { render: t('toasts.evergreen.fetching') });
                return fetchEvergreenLatestRelease()
                    .then(latest => {
                        if (latest.name !== infos.version && Date.parse(latest.published_at) > Date.parse(infos.timestamp)) {
                            toast.update(toastId, { type: toast.TYPE.SUCCESS, render: <><p>{t('toasts.evergreen.fetched.newRelease.label')}</p><p><a href={latest.html_url}>{t('toasts.evergreen.fetched.newRelease.link', { version: latest.name })}</a></p></> });
                        } else {
                            toast.update(toastId, { type: toast.TYPE.INFO, render: t('toasts.evergreen.fetched.upToDate', { version: infos.version }), autoClose: 5000 });
                        }
                        if (!announceOptOut) {
                            return fetchEvergreenAnnounce()
                                .then(announce => {
                                    dispatch(setAnnounce(announce));
                                })
                                .catch(e => {
                                    console.error('failed to fetch announce', e);
                                });
                        }
                    })
                    .catch(e => {
                        console.error('failed to fetch latest release', e);
                        toast.update(toastId, { type: toast.TYPE.ERROR, render: <IssueReportToast content={<>{t('toasts.evergreen.fetchingFailed')}</>} error={e} />, autoClose: false });
                    });
            })
            .catch(e => {
                console.error('failed to fetch current version', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: <IssueReportToast content={<>{t('toasts.evergreen.loadingFailed')}</>} error={e} />, autoClose: false });
            });
    }
};
