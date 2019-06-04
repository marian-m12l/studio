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

    render() {
        return (<div className="pack-diagram-widget">

            {/* Metadata */}
            <label htmlFor="pack-title">Title:</label>
            <input id="pack-title" type="text" value={this.props.diagramEngine.getDiagramModel().title} onChange={this.changeTitle}/>
            <label htmlFor="pack-version">Version:</label>
            <input id="pack-version" type="number" value={this.props.diagramEngine.getDiagramModel().version} onChange={this.changeVersion}/>

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
