/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import * as SRD from 'storm-react-diagrams';

import StageNodeModel from "../models/StageNodeModel";
import ActionNodeModel from "../models/ActionNodeModel";
import TrayWidget from "./TrayWidget";
import TrayItemWidget from "./TrayItemWidget";
import {toast} from "react-toastify";


class PackDiagramWidget extends React.Component {

    constructor(props) {
        super(props);
    }

    changeTitle = (e) => {
        this.props.diagramEngine.getDiagramModel().title = e.target.value;
        this.forceUpdate();
    };

    changeVersion = (e) => {
        this.props.diagramEngine.getDiagramModel().version = e.target.value;
        this.forceUpdate();
    };

    changeDescription = (e) => {
        this.props.diagramEngine.getDiagramModel().description = e.target.value;
        this.forceUpdate();
    };

    showThumbnailSelector = () => {
        document.getElementById('pack-thumb').click();
    };

    changeThumbnail = (event) => {
        let file = event.target.files[0];
        if (file.type !== 'image/png') {
            toast.error("Thumbnail must be in PNG format.");
            return;
        }
        let reader = new FileReader();
        let that = this;
        reader.addEventListener("load", () => {
            console.log(reader.result);
            that.props.diagramEngine.getDiagramModel().thumbnail = reader.result;
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    render() {
        let defaultImage = 'data:image/png;base64,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';
        return (<div className="pack-diagram-widget">

            {/* Metadata */}
            <label htmlFor="pack-title">Title:</label>
            <input id="pack-title" type="text" value={this.props.diagramEngine.getDiagramModel().title} onChange={this.changeTitle}/>
            <label htmlFor="pack-version">Version:</label>
            <input id="pack-version" type="number" value={this.props.diagramEngine.getDiagramModel().version} onChange={this.changeVersion}/>
            <label htmlFor="pack-description">Description:</label>
            <textarea id="pack-description" value={this.props.diagramEngine.getDiagramModel().description} style={{display: 'inline-block'}} onChange={this.changeDescription}/>
            <label htmlFor="pack-thumb">Thumbnail:</label>
            <input type="file" id="pack-thumb" style={{visibility: 'hidden', position: 'absolute'}} onChange={this.changeThumbnail} />
            <img src={this.props.diagramEngine.getDiagramModel().thumbnail || defaultImage} width="128" height="128" onClick={this.showThumbnailSelector} />

            <div className="content">
                {/* Node tray */}
                <TrayWidget>
                    <TrayItemWidget model={{ type: "stage" }} name="Stage Node" color="#919e3d" />
                    <TrayItemWidget model={{ type: "action" }} name="Action Node" color="#9e7a34" />
                </TrayWidget>

                {/* Diagram */}
                <div className="diagram-drop-zone"
                     onDrop={event => {
                         event.preventDefault();
                         let nodeData = event.dataTransfer.getData("storm-diagram-node");
                         if (!nodeData) {
                             // Ignore missing node data
                             return;
                         }
                         var data = JSON.parse(nodeData);
                         var node = null;
                         if (data.type === "stage") {
                             node = new StageNodeModel("Stage node");
                         } else {
                             node = new ActionNodeModel("Action node");
                         }
                         var points = this.props.diagramEngine.getRelativeMousePoint(event);
                         node.setPosition(points.x, points.y);
                         this.props.diagramEngine.getDiagramModel().addNode(node);
                         this.forceUpdate();
                     }}
                     onDragOver={event => {
                         event.preventDefault();
                     }}>
                    <SRD.DiagramWidget className="storm-diagrams-canvas" diagramEngine={this.props.diagramEngine}/>
                </div>
            </div>

        </div>);
    }

}

PackDiagramWidget.propTypes = {
    diagramEngine: PropTypes.instanceOf(SRD.DiagramEngine).isRequired
};

export default PackDiagramWidget;
