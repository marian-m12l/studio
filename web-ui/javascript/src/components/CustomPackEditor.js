/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import * as SRD from 'storm-react-diagrams';
import 'storm-react-diagrams/dist/style.min.css';

import StageNodeFactory from "./factories/StageNodeFactory";
import ActionNodeFactory from "./factories/ActionNodeFactory";
import PackDiagramModel from "./models/PackDiagramModel";
import PackDiagramWidget from "./widgets/PackDiagramWidget";
import {writeToArchive} from "../utils/writer";
import {readFromArchive} from "../utils/reader";
import {sample} from "../utils/sample";

import './CustomPackEditor.css';


class CustomPackEditor extends React.Component {

    constructor(props) {
        super(props);

        this.engine = new SRD.DiagramEngine();
        this.engine.installDefaultFactories();

        let updateCanvas = () => {
            this.forceUpdate();
        };
        this.engine.registerNodeFactory(new StageNodeFactory(updateCanvas));
        this.engine.registerNodeFactory(new ActionNodeFactory(updateCanvas));

        let model = sample();

        this.engine.setDiagramModel(model);
    }

    clear = () => {
        this.engine.setDiagramModel(new PackDiagramModel());
        this.forceUpdate();
    };

    savePack = () => {
        writeToArchive(this.engine.diagramModel).then(blob => {
            var a = document.getElementById('download');
            a.href = URL.createObjectURL(blob);
            a.download = this.engine.diagramModel.title + '.zip';
            a.click();
            URL.revokeObjectURL(a.href);
        });
    };

    showFileSelector = () => {
        document.getElementById('upload').click();
    };

    packFileSelected = (event) => {
        let file = event.target.files[0];
        console.log('Selected file name = ' + file.name);
        if (file.type !== 'application/zip') {
            // TODO error notification
            return;
        }

        this.loadPack(file);
    };

    loadPack = (file) => {
        readFromArchive(file).then(loadedModel => {
            this.engine.setDiagramModel(loadedModel);
            this.forceUpdate();
        });
    };

    render() {
        return (
            <div className="custom-pack-editor">
                <a id="download" style={{visibility: 'hidden'}} />
                <input type="file" id="upload" style={{visibility: 'hidden'}} onChange={this.packFileSelected} />
                <span title="Load pack" className="btn btn-default glyphicon glyphicon-folder-open" onClick={this.showFileSelector}/>
                <span title="Save pack" className="btn btn-default glyphicon glyphicon-floppy-disk" onClick={this.savePack}/>
                <span title="Clear pack" className="btn btn-default glyphicon glyphicon-trash" onClick={this.clear}/>

                <PackDiagramWidget diagramEngine={this.engine}/>
            </div>
        );
    }
}

export default CustomPackEditor;
