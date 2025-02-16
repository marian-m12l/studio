import { readFromArchive } from "../utils/reader";
import { simplifiedSample } from "../utils/sample";
import { generateFilename } from "../utils/packs";
import { toast } from 'react-toastify';
import IssueReportToast from "../components/IssueReportToast";
import { StoryPack } from '../../@types/pack';
import { t } from "i18next";
import { setEditorDiagram, showEditor } from ".";
import PackDiagramModel from "../components/diagram/models/PackDiagramModel";

export const actionLoadPackInEditor = (packData:StoryPack, filename:string) => {
    return dispatch => {
        const toastId = toast(t('toasts.editor.loading'), { autoClose: false });
        readFromArchive(packData)
            .then(loadedModel => {
                toast.update(toastId, { type: "success", render: t('toasts.editor.loaded'), autoClose: 5000 });
                dispatch(setEditorDiagram(loadedModel, filename));
                dispatch(showEditor());
            })
            .catch(e => {
                console.error('failed to load story pack', e);
                toast.update(toastId, { type: "error", render: <IssueReportToast content={t('toasts.editor.loadingFailed')} error={e} />, autoClose: false });
            });
    }
};

export const actionCreatePackInEditor = () => {
    return dispatch => {
        const model = new PackDiagramModel();
        dispatch(setEditorDiagram(model));
        dispatch(showEditor());
    }
};

export const actionLoadSampleInEditor = () => {
    return dispatch => {
        const model = simplifiedSample();
        dispatch(setEditorDiagram(model, generateFilename(model)));
        dispatch(showEditor());
    }
};