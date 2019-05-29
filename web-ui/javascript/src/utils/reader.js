/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as dagre from 'dagre';
import JSZip from 'jszip';

import StageNodeModel from "../components/models/StageNodeModel";
import ActionNodeModel from "../components/models/ActionNodeModel";
import PackDiagramModel from "../components/models/PackDiagramModel";


export function readFromArchive(file) {
    // Read zip archive
    var zip = new JSZip();
    return zip.loadAsync(file)
        .then((archive) => {
            // Read JSON story descriptor
            return archive.file("story.json").async('string').then(storyJson => {
                let json = JSON.parse(storyJson);

                var loadedModel = new PackDiagramModel(json.title, json.version);

                let links = [];

                let actionNodes = new Map(
                    json.actionNodes.map(node => {
                        // Build action node
                        var actionNode = new ActionNodeModel(node.name);
                        if (node.position) {
                            actionNode.setPosition(node.position.x, node.position.y);
                        }
                        return [node.id, actionNode];
                    })
                );

                let assetsPromises = [];

                let stageNodes = new Map(
                    json.stageNodes.map(node => {
                        // Build stage node
                        var stageNode = new StageNodeModel(node.name, node.uuid);
                        // Async load from asset files
                        let imagePromise = new Promise((resolve, reject) => {
                            if (node.image) {
                                archive.file('assets/'+node.image).async('base64').then(base64Asset => {
                                    resolve('data:image/bmp;base64,' + base64Asset);
                                });
                            } else {
                                resolve(null);
                            }
                        });
                        let audioPromise = new Promise((resolve, reject) => {
                            if (node.audio) {
                                archive.file('assets/'+node.audio).async('base64').then(base64Asset => {
                                    resolve('data:sound/x-wav;base64,' + base64Asset);
                                });
                            } else {
                                resolve(null);
                            }
                        });
                        Promise.all([imagePromise, audioPromise]).then(assets => {
                            stageNode.image = assets[0];
                            stageNode.audio = assets[1];
                        });
                        // Will have to wait for asset promises
                        assetsPromises.push(imagePromise);
                        assetsPromises.push(audioPromise);

                        stageNode.setControl('wheel', node.controlSettings.wheel);
                        stageNode.setControl('ok', node.controlSettings.ok);
                        stageNode.setControl('home', node.controlSettings.home);
                        stageNode.setControl('pause', node.controlSettings.pause);
                        stageNode.setControl('autoplay', node.controlSettings.autoplay);
                        if (node.okTransition) {
                            let actionNode = actionNodes.get(node.okTransition.actionNode);
                            while (actionNode.optionsIn.length <= node.okTransition.optionIndex) {
                                actionNode.addOption();
                            }
                            let optionPort = actionNode.optionsIn[node.okTransition.optionIndex];
                            links.push(stageNode.okPort.link(optionPort))
                        }
                        if (node.homeTransition) {
                            let actionNode = actionNodes.get(node.homeTransition.actionNode);
                            while (actionNode.optionsIn.length <= node.homeTransition.optionIndex) {
                                actionNode.addOption();
                            }
                            let optionPort = actionNode.optionsIn[node.homeTransition.optionIndex];
                            links.push(stageNode.homePort.link(optionPort))
                        }
                        if (node.position) {
                            stageNode.setPosition(node.position.x, node.position.y);
                        }
                        return [node.uuid, stageNode];
                    })
                );

                // Add options links from action nodes to stage nodes
                json.actionNodes.forEach(node => {
                    var actionNode = actionNodes.get(node.id);
                    node.options.forEach((opt, idx) => {
                        while (actionNode.optionsOut.length <= idx) {
                            actionNode.addOption();
                        }
                        let optionPort = actionNode.optionsOut[idx];
                        if (opt) {
                            links.push(optionPort.link(stageNodes.get(opt).fromPort));
                        }
                    });
                });


                // Wait for asset promises
                return Promise.all(assetsPromises)
                    .then(assets => {
                        stageNodes.forEach(node => loadedModel.addNode(node));
                        actionNodes.forEach(node => loadedModel.addNode(node));
                        links.forEach(link => loadedModel.addLink(link));

                        // Auto distribute nodes if positions are missing
                        let missingPositions = json.actionNodes.concat(json.stageNodes)
                            .filter(node =>
                                typeof node.position === 'undefined' || node.position == null || (node.position.x === 0 && node.position.y === 0)
                            ).length > 0;
                        console.log(missingPositions ? "POSITIONS ARE MISSING. AUTO DISTRIBUTE" : "POSITIONS ARE SET");
                        if (missingPositions) {
                            let distributedNodes = distributeGraph(loadedModel);
                            distributedNodes.forEach(node => {
                                let modelNode = loadedModel.getNode(node.id);
                                modelNode.x = node.x - node.width / 2;
                                modelNode.y = node.y - node.height / 2;
                            });
                        }

                        return loadedModel;
                    });
            });

        }, e => {
            console.log("Error reading " + file.name + ": " + e.message)
        });

}



const size = {
    width: 220,
    height: 160
};

function distributeGraph(model) {
    let graph = new dagre.graphlib.Graph();
    // Configure graph layout
    graph.setGraph({
        rankdir: 'LR',  // Left-to-right
        marginx: 30,
        marginy: 30
    });
    graph.setDefaultEdgeLabel(() => ({}));

    Object.values(model.nodes)
        .forEach(node => {
            graph.setNode(node.id, { ...size, id: node.id });
        });

    Object.values(model.links)
        .map(link => {
            return {
                source: link.sourcePort.getNode().getID(),
                target: link.targetPort.getNode().getID()
            }
        })
        .filter(link => link.source && link.target && model.getNode(link.source) && model.getNode(link.target))
        .forEach(link => {
            graph.setEdge(link.source, link.target);
        });

    //auto-distribute
    dagre.layout(graph);
    return graph.nodes().map(node => graph.node(node));
}
