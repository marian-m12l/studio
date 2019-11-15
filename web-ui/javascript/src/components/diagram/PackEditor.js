/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import {connect} from "react-redux";
import { toast } from 'react-toastify';
import * as SRD from 'storm-react-diagrams';
import {withTranslation} from "react-i18next";
import 'storm-react-diagrams/dist/style.min.css';

import StageNodeFactory from "./factories/StageNodeFactory";
import ActionNodeFactory from "./factories/ActionNodeFactory";
import CoverNodeFactory from "./factories/CoverNodeFactory";
import MenuNodeFactory from "./factories/MenuNodeFactory";
import StoryNodeFactory from "./factories/StoryNodeFactory";
import PackDiagramModel from "./models/PackDiagramModel";
import StageNodeModel from "./models/StageNodeModel";
import PackDiagramWidget from "./widgets/PackDiagramWidget";
import Modal from "../Modal";
import {writeToArchive} from "../../utils/writer";
import {
    actionLoadPackInEditor,
    actionUploadToLibrary,
    setEditorDiagram
} from "../../actions";

import './PackEditor.css';


class PackEditor extends React.Component {

    constructor(props) {
        super(props);

        let engine = new SRD.DiagramEngine();
        engine.installDefaultFactories();

        let updateCanvas = () => {
            this.forceUpdate();
        };
        engine.registerNodeFactory(new StageNodeFactory(updateCanvas));
        engine.registerNodeFactory(new ActionNodeFactory(updateCanvas));
        engine.registerNodeFactory(new CoverNodeFactory(updateCanvas));
        engine.registerNodeFactory(new MenuNodeFactory(updateCanvas));
        engine.registerNodeFactory(new StoryNodeFactory(updateCanvas));

        this.state = {
            engine,
            diagram: null,
            showSaveConfirmDialog: false
        };
    }

    componentDidMount() {
        if (this.props.editor.diagram) {
            let engine = this.state.engine;
            engine.setDiagramModel(this.props.editor.diagram);
            this.setState({
                engine,
                diagram: this.props.editor.diagram
            });
        }
    }

    componentWillReceiveProps(nextProps, nextContext) {
        if (nextProps.editor.diagram && nextProps.editor.diagram !== this.state.diagram) {
            let engine = this.state.engine;
            engine.setDiagramModel(nextProps.editor.diagram);
            this.setState({
                engine,
                diagram: nextProps.editor.diagram
            });
        }
    }

    savePackToLibrary = () => {
        // Pack path is either stored (when open from library) or generated
        let uuid = Object.values(this.state.engine.diagramModel.nodes)
            .filter(node => node.squareOne)[0].getUuid();
        let path = this.props.editor.libraryPath || this.state.engine.diagramModel.title.replace(' ', '_') + '-' + uuid + '-v' + this.state.engine.diagramModel.version + '.zip';
        // Confirmation dialog if pack already in library
        if (this.props.library.packs.filter(pack => pack.path === path).length > 0) {
            this.showSaveConfirmDialog();
        } else {
            this.doSavePackToLibrary();
        }
    };

    doSavePackToLibrary = () => {
        const { t } = this.props;
        // Pack path is either stored (when open from library) or generated
        let uuid = Object.values(this.state.engine.diagramModel.nodes)
            .filter(node => node.squareOne)[0].getUuid();
        let path = this.props.editor.libraryPath || this.state.engine.diagramModel.title.replace(' ', '_') + '-' + uuid + '-v' + this.state.engine.diagramModel.version + '.zip';
        // Show toast
        let toastId = toast(t('toasts.editor.saving'), { autoClose: false });
        writeToArchive(this.state.engine.diagramModel)
            .then(blob => {
                this.props.uploadPackToLibrary(uuid, path, blob)
                    .then(() => toast.update(toastId, { type: toast.TYPE.SUCCESS, render: t('toasts.editor.saved'), autoClose: 5000}));
            })
            .catch(e => {
                console.error('failed to save story pack to library', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.editor.savingFailed'), autoClose: 5000 });
            })
            .finally(() => {
                // Always hide confirmation dialog
                this.dismissSaveConfirmDialog();
            });
    };

    clear = () => {
        let model = new PackDiagramModel();

        // Set 'square one' stage node
        let packSelectionNode = new StageNodeModel("Pack selection stage");
        packSelectionNode.squareOne = true;
        packSelectionNode.setControl('wheel', true);
        packSelectionNode.setControl('ok', true);
        packSelectionNode.setPosition(50, 200);

        model.addNode(packSelectionNode);

        this.props.setEditorDiagram(model);
    };

    exportPack = () => {
        const { t } = this.props;
        let toastId = toast(t('toasts.editor.exporting'), { autoClose: false });
        writeToArchive(this.state.engine.diagramModel)
            .then(blob => {
                toast.update(toastId, { type: toast.TYPE.SUCCESS, render: t('toasts.editor.exported'), autoClose: 5000});
                var a = document.getElementById('download');
                a.href = URL.createObjectURL(blob);
                a.download = this.state.engine.diagramModel.title + '.zip';
                a.click();
                URL.revokeObjectURL(a.href);
            })
            .catch(e => {
                console.error('failed to export story pack', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: t('toasts.editor.exportingFailed'), autoClose: 5000 });
            });
    };

    showImportFileSelector = () => {
        document.getElementById('upload').click();
    };

    packImportFileSelected = (event) => {
        const { t } = this.props;
        let file = event.target.files[0];
        console.log('Selected import file name = ' + file.name);
        console.log(file.type);
        if (['application/zip', 'application/x-zip-compressed'].indexOf(file.type) === -1) {
            toast.error(t('toasts.editor.loadingWrongType'));
            return;
        }

        this.importPack(file);
    };

    importPack = (file) => {
        this.props.loadPackInEditor(file);
    };

    showSaveConfirmDialog = () => {
        this.setState({showSaveConfirmDialog: true});
    };

    dismissSaveConfirmDialog = () => {
        this.setState({showSaveConfirmDialog: false});
    };

    render() {
        const { t } = this.props;
        return (
            <div className="custom-pack-editor">
                {this.state.showSaveConfirmDialog &&
                <Modal id="confirm-library-pack-overwrite"
                       title={t('dialogs.editor.save.title')}
                       content={<p>{t('dialogs.editor.save.content')}</p>}
                       buttons={[
                           { label: t('dialogs.shared.no'), onClick: this.dismissSaveConfirmDialog},
                           { label: t('dialogs.shared.yes'), onClick: this.doSavePackToLibrary}
                       ]}
                       onClose={this.dismissSaveConfirmDialog}
                />}
                <a id="download" style={{visibility: 'hidden', position: 'absolute'}} />
                <span title={t('editor.actions.save')} className="btn btn-default glyphicon glyphicon-floppy-disk" onClick={this.savePackToLibrary}/>
                <input type="file" id="upload" style={{visibility: 'hidden', position: 'absolute'}} onChange={this.packImportFileSelected} />
                <span title={t('editor.actions.import')} className="btn btn-default glyphicon glyphicon-import" onClick={this.showImportFileSelector}/>
                <span title={t('editor.actions.export')} className="btn btn-default glyphicon glyphicon-export" onClick={this.exportPack}/>
                <span title={t('editor.actions.clear')} className="btn btn-default glyphicon glyphicon-trash" onClick={this.clear}/>

                <PackDiagramWidget diagramEngine={this.state.engine}/>
            </div>
        );
    }
}

const mapStateToProps = (state, ownProps) => ({
    editor: state.editor,
    library: state.library
});

const mapDispatchToProps = (dispatch, ownProps) => ({
    setEditorDiagram: (diagram) => dispatch(setEditorDiagram(diagram)),
    loadPackInEditor: (packData) => dispatch(actionLoadPackInEditor(packData, null, ownProps.t)),
    uploadPackToLibrary: (uuid, path, packData) => dispatch(actionUploadToLibrary(uuid, path, packData, ownProps.t))
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(PackEditor)
)
