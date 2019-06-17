/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import {connect} from "react-redux";
import { toast } from 'react-toastify';
import * as SRD from 'storm-react-diagrams';
import 'storm-react-diagrams/dist/style.min.css';

import StageNodeFactory from "./factories/StageNodeFactory";
import ActionNodeFactory from "./factories/ActionNodeFactory";
import PackDiagramModel from "./models/PackDiagramModel";
import PackDiagramWidget from "./widgets/PackDiagramWidget";
import {writeToArchive} from "../../utils/writer";
import {readFromArchive} from "../../utils/reader";

import './PackEditor.css';
import {setEditorDiagram} from "../../actions";
import StageNodeModel from "./models/StageNodeModel";


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

        this.state = {
            engine,
            diagram: null
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

    savePack = () => {
        let toastId = toast("Saving story pack...", { autoClose: false });
        writeToArchive(this.state.engine.diagramModel)
            .then(blob => {
                toast.update(toastId, { type: toast.TYPE.SUCCESS, render: 'Saved story pack.', autoClose: 5000});
                var a = document.getElementById('download');
                a.href = URL.createObjectURL(blob);
                a.download = this.state.engine.diagramModel.title + '.zip';
                a.click();
                URL.revokeObjectURL(a.href);
            })
            .catch(e => {
                console.error('failed to save story pack', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: 'Failed to save story pack.', autoClose: 5000 });
            });
    };

    showFileSelector = () => {
        document.getElementById('upload').click();
    };

    packFileSelected = (event) => {
        let file = event.target.files[0];
        console.log('Selected file name = ' + file.name);
        if (file.type !== 'application/zip') {
            toast.error("Story pack file must be in zip archive format.");
            return;
        }

        this.loadPack(file);
    };

    loadPack = (file) => {
        let toastId = toast("Loading story pack...", { autoClose: false });
        readFromArchive(file)
            .then(loadedModel => {
                toast.update(toastId, { type: toast.TYPE.SUCCESS, render: 'Loaded story pack.', autoClose: 5000});
                this.props.setEditorDiagram(loadedModel);
            })
            .catch(e => {
                console.error('failed to load story pack', e);
                toast.update(toastId, { type: toast.TYPE.ERROR, render: 'Failed to load story pack.', autoClose: 5000 });
            });
    };

    render() {
        return (
            <div className="custom-pack-editor">
                <a id="download" style={{visibility: 'hidden', position: 'absolute'}} />
                <input type="file" id="upload" style={{visibility: 'hidden', position: 'absolute'}} onChange={this.packFileSelected} />
                <span title="Load pack" className="btn btn-default glyphicon glyphicon-folder-open" onClick={this.showFileSelector}/>
                <span title="Save pack" className="btn btn-default glyphicon glyphicon-floppy-disk" onClick={this.savePack}/>
                <span title="Clear pack" className="btn btn-default glyphicon glyphicon-trash" onClick={this.clear}/>

                <PackDiagramWidget diagramEngine={this.state.engine}/>
            </div>
        );
    }
}

const mapStateToProps = (state, ownProps) => ({
    editor: state.editor
});

const mapDispatchToProps = (dispatch, ownProps) => ({
    setEditorDiagram: (diagram) => dispatch(setEditorDiagram(diagram))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(PackEditor)

