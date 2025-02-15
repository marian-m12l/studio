/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import * as dagre from 'dagre';
import JSZip from 'jszip';

import StageNodeModel from "../components/diagram/models/StageNodeModel";
import ActionNodeModel from "../components/diagram/models/ActionNodeModel";
import CoverNodeModel from "../components/diagram/models/CoverNodeModel";
import MenuNodeModel from "../components/diagram/models/MenuNodeModel";
import StoryNodeModel from "../components/diagram/models/StoryNodeModel";
import PackDiagramModel from "../components/diagram/models/PackDiagramModel";


export function readFromArchive(file) {
    // Read zip archive
    var zip = new JSZip();
    return zip.loadAsync(file)
        .then((archive) => {
            // Read JSON story descriptor
            return archive.file("story.json").async('string').then(storyJson => {
                let json = JSON.parse(storyJson);

                var loadedModel = new PackDiagramModel(json.title, json.version, (json.description || ''), (json.nightModeAvailable || false));

                let links = [];

                // Async load thumbnail file
                let thumbnailPromise = new Promise((resolve, reject) => {
                    let thumb = archive.file('thumbnail.png');
                    if (thumb) {
                        thumb.async('base64').then(base64Thumb => {
                            resolve(dataUrlPrefix('thumbnail.png') + base64Thumb);
                        });
                    } else {
                        resolve(null);
                    }
                }).then(thumb => loadedModel.thumbnail = thumb);

                let assetsPromises = [
                    thumbnailPromise
                ];


                // First, load nodes ignoring transitions

                // Actual action nodes
                let actionNodes = new Map(
                    json.actionNodes.filter(node => (node.type || 'action') === 'action').map(node => {
                        // Build action node
                        var actionNode = new ActionNodeModel({ name: node.name });
                        if (node.position) {
                            actionNode.setPosition(node.position.x, node.position.y);
                        }
                        return [node.id, actionNode];
                    })
                );

                // Group 'virtual' action and stage nodes
                let virtualNodes = json.actionNodes.filter(node => (node.type || 'action') !== 'action')
                    .concat(json.stageNodes.filter(node => (node.type || 'stage') !== 'stage'))
                    .reduce(
                        (acc, node) => {
                            if (!acc[node.groupId]) {
                                acc[node.groupId] = [];
                            }
                            acc[node.groupId].push(node);
                            return acc;
                        },
                        {}
                    );
                console.log(virtualNodes);

                // Build simplified nodes from 'virtual' nodes
                let simplifiedNodes = new Map(
                    Object.values(virtualNodes).map(group => {
                        // Story node
                        if (group[0].type.startsWith('story')) {
                            let storyVirtualStage = group.find(node => node.type === 'story');
                            let storyVirtualAction = group.find(node => node.type === 'story.storyaction');
                            let storyNode = new StoryNodeModel({ name: storyVirtualStage.name, uuid: storyVirtualStage.groupId });
                            if (storyVirtualStage.controlSettings.home === false) {
                                storyNode.setDisableHome(true);
                            }
                            // Async load from asset files
                            let audioPromise = new Promise((resolve, reject) => {
                                if (storyVirtualStage.audio) {
                                    archive.file('assets/' + storyVirtualStage.audio).async('base64').then(base64Asset => {
                                        resolve(dataUrlPrefix(storyVirtualStage.audio) + base64Asset);
                                    });
                                } else {
                                    resolve(null);
                                }
                            }).then(audio => storyNode.setAudio(audio));
                            // Will have to wait for asset promises
                            assetsPromises.push(audioPromise);
                            if (storyVirtualAction.position) {
                                storyNode.setPosition(storyVirtualAction.position.x, storyVirtualAction.position.y);
                            }
                            return [storyVirtualAction.id, storyNode];
                        }
                        // Menu node
                        else if (group[0].type.startsWith('menu')) {
                            let menuQuestionVirtualStage = group.find(node => node.type === 'menu.questionstage');
                            let menuQuestionVirtualAction = group.find(node => node.type === 'menu.questionaction');
                            let menuNode = new MenuNodeModel({ name: menuQuestionVirtualStage.name, uuid: menuQuestionVirtualStage.groupId });
                            // Async load from asset files
                            let audioPromise = new Promise((resolve, reject) => {
                                if (menuQuestionVirtualStage.audio) {
                                    archive.file('assets/' + menuQuestionVirtualStage.audio).async('base64').then(base64Asset => {
                                        resolve(dataUrlPrefix(menuQuestionVirtualStage.audio) + base64Asset);
                                    });
                                } else {
                                    resolve(null);
                                }
                            }).then(audio => menuNode.setQuestionAudio(audio));
                            // Will have to wait for asset promises
                            assetsPromises.push(audioPromise);
                            // Options
                            group.filter(node => node.type === 'menu.optionstage').forEach((optionVirtualStage, idx) => {
                                menuNode.addOption();
                                menuNode.setOptionName(idx, optionVirtualStage.name);
                                // Async load from asset files
                                let imagePromise = new Promise((resolve, reject) => {
                                    if (optionVirtualStage.image) {
                                        archive.file('assets/' + optionVirtualStage.image).async('base64').then(base64Asset => {
                                            resolve(dataUrlPrefix(optionVirtualStage.image) + base64Asset);
                                        });
                                    } else {
                                        resolve(null);
                                    }
                                }).then(image => menuNode.setOptionImage(idx, image));
                                let audioPromise = new Promise((resolve, reject) => {
                                    if (optionVirtualStage.audio) {
                                        archive.file('assets/' + optionVirtualStage.audio).async('base64').then(base64Asset => {
                                            resolve(dataUrlPrefix(optionVirtualStage.audio) + base64Asset);
                                        });
                                    } else {
                                        resolve(null);
                                    }
                                }).then(audio => menuNode.setOptionAudio(idx, audio));
                                // Will have to wait for asset promises
                                assetsPromises.push(imagePromise);
                                assetsPromises.push(audioPromise);
                            });
                            if (menuQuestionVirtualAction.position) {
                                menuNode.setPosition(menuQuestionVirtualAction.position.x, menuQuestionVirtualAction.position.y);
                            }
                            // Default option policy
                            menuNode.setDefaultOption(menuQuestionVirtualStage.okTransition.optionIndex);
                            return [menuQuestionVirtualAction.id, menuNode];
                        }
                        // Cover node (TODO make sure there is only one start node !)
                        else if (group[0].type.startsWith('cover')) {
                            let coverNode = new CoverNodeModel({ name: group[0].name, uuid: group[0].uuid });
                            // Async load from asset files
                            let imagePromise = new Promise((resolve, reject) => {
                                if (group[0].image) {
                                    archive.file('assets/' + group[0].image).async('base64').then(base64Asset => {
                                        resolve(dataUrlPrefix(group[0].image) + base64Asset);
                                    });
                                } else {
                                    resolve(null);
                                }
                            }).then(image => coverNode.setImage(image));
                            let audioPromise = new Promise((resolve, reject) => {
                                if (group[0].audio) {
                                    archive.file('assets/' + group[0].audio).async('base64').then(base64Asset => {
                                        resolve(dataUrlPrefix(group[0].audio) + base64Asset);
                                    });
                                } else {
                                    resolve(null);
                                }
                            }).then(audio => coverNode.setAudio(audio));
                            // Will have to wait for asset promises
                            assetsPromises.push(imagePromise);
                            assetsPromises.push(audioPromise);
                            if (group[0].position) {
                                coverNode.setPosition(group[0].position.x, group[0].position.y);
                            }
                            return [coverNode.uuid, coverNode];
                        }
                        else {
                            // TODO error
                            console.log('UNKNOWN SIMPLIFIED NODES GROUP: %o', group);
                            return [null, null];
                        }
                    })
                );
                console.log(simplifiedNodes);

                // Actual stage nodes
                let stageNodes = new Map(
                    json.stageNodes.filter(node => (node.type || 'stage') === 'stage').map(node => {
                        // Build stage node
                        var stageNode = new StageNodeModel({ name: node.name, uuid: node.uuid });
                        // Square one
                        stageNode.setSquareOne(node.squareOne || false);
                        // Async load from asset files
                        let imagePromise = new Promise((resolve, reject) => {
                            if (node.image) {
                                archive.file('assets/'+node.image).async('base64').then(base64Asset => {
                                    resolve(dataUrlPrefix(node.image) + base64Asset);
                                });
                            } else {
                                resolve(null);
                            }
                        }).then(image => stageNode.setImage(image));
                        let audioPromise = new Promise((resolve, reject) => {
                            if (node.audio) {
                                archive.file('assets/'+node.audio).async('base64').then(base64Asset => {
                                    resolve(dataUrlPrefix(node.audio) + base64Asset);
                                });
                            } else {
                                resolve(null);
                            }
                        }).then(audio => stageNode.setAudio(audio));

                        // Will have to wait for asset promises
                        assetsPromises.push(imagePromise);
                        assetsPromises.push(audioPromise);

                        stageNode.setControl('wheel', node.controlSettings.wheel);
                        stageNode.setControl('ok', node.controlSettings.ok);
                        stageNode.setControl('home', node.controlSettings.home);
                        stageNode.setControl('pause', node.controlSettings.pause);
                        stageNode.setControl('autoplay', node.controlSettings.autoplay);
                        if (node.position) {
                            stageNode.setPosition(node.position.x, node.position.y);
                        }
                        return [node.uuid, stageNode];
                    })
                );


                // Then, add links / transitions between nodes

                // Add options links from actual action nodes to actual stage nodes
                json.actionNodes.filter(node => (node.type || 'action') === 'action').forEach(node => {
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

                // Add links from actual stage nodes to actual action nodes or simplified nodes
                json.stageNodes.filter(node => (node.type || 'stage') === 'stage').forEach(node => {
                    var stageNode = stageNodes.get(node.uuid);
                    if (node.okTransition) {
                        links.push(stageNode.okPort.link(getTransitionTargetNode(node.okTransition, actionNodes, simplifiedNodes)));
                    }
                    // Make sure home port exists, because a bug in pack writer produced incorrect story packs with home transition and disabled home button (#100)
                    if (node.homeTransition && stageNode.homePort) {
                        links.push(stageNode.homePort.link(getTransitionTargetNode(node.homeTransition, actionNodes, simplifiedNodes)))
                    }
                });

                // Add links from simplified nodes
                Object.values(virtualNodes).forEach(group => {
                    // Story node
                    if (group[0].type.startsWith('story')) {
                        let storyVirtualStage = group.find(node => node.type === 'story');
                        let storyVirtualAction = group.find(node => node.type === 'story.storyaction');
                        let storyNode = simplifiedNodes.get(storyVirtualAction.id);
                        // Add 'ok' and 'home' transitions if they do not point to the 'first useful node' (default behaviour)
                        let coverNode = json.stageNodes.filter(node => node.squareOne)
                            .concat(Object.values(virtualNodes).filter(grp => grp[0].type.startsWith('cover')).map(grp => grp[0]))[0];
                        let firstUsefulNodeUuid = coverNode.okTransition.actionNode;
                        if (storyVirtualStage.okTransition.actionNode !== firstUsefulNodeUuid) {
                            // Enable custom OK transition to create OK port
                            storyNode.setCustomOkTransition(true);
                            // Create link
                            links.push(storyNode.okPort.link(getTransitionTargetNode(storyVirtualStage.okTransition, actionNodes, simplifiedNodes)));
                        }
                        // Home transition may be disabled
                        if (!storyNode.disableHome && storyVirtualStage.homeTransition.actionNode !== firstUsefulNodeUuid) {
                            // Enable custom Home transition to create Home port
                            storyNode.setCustomHomeTransition(true);
                            // Create link
                            links.push(storyNode.homePort.link(getTransitionTargetNode(storyVirtualStage.homeTransition, actionNodes, simplifiedNodes)));
                        }
                        simplifiedNodes.set(storyVirtualAction.id, storyNode);
                    }
                    // Menu node: options transitions
                    else if (group[0].type.startsWith('menu')) {
                        let menuQuestionVirtualAction = group.find(node => node.type === 'menu.questionaction');
                        let menuNode = simplifiedNodes.get(menuQuestionVirtualAction.id);
                        // Options transitions
                        group.filter(node => node.type === 'menu.optionstage').forEach((optionVirtualStage, idx) => {
                            if (optionVirtualStage.okTransition) {
                                links.push(menuNode.optionsOut[idx].link(getTransitionTargetNode(optionVirtualStage.okTransition, actionNodes, simplifiedNodes)));
                            }
                        });
                    }
                    // Cover node: OK transition
                    else if (group[0].type.startsWith('cover')) {
                        let coverNode = simplifiedNodes.get(group[0].uuid);
                        if (group[0].okTransition) {
                            links.push(coverNode.okPort.link(getTransitionTargetNode(group[0].okTransition, actionNodes, simplifiedNodes)));
                        }
                    }
                    else {
                        // TODO error
                        console.log('UNKNOWN SIMPLIFIED NODES GROUP: %o', group);
                    }
                });


                // Wait for asset promises
                return Promise.all(assetsPromises)
                    .then(assets => {
                        stageNodes.forEach(node => loadedModel.addNode(node));
                        actionNodes.forEach(node => loadedModel.addNode(node));
                        simplifiedNodes.forEach(node => loadedModel.addNode(node));
                        links.forEach(link => loadedModel.addLink(link));

                        // Auto distribute nodes if positions are missing
                        let missingPositions = json.actionNodes
                            .filter(node => (node.type || 'action') === 'action' || (node.type || 'action') === 'menu.questionaction' || (node.type || 'action') === 'story.storyaction')
                            .concat(json.stageNodes.filter(node => (node.type || 'stage') === 'stage' || (node.type || 'stage') === 'cover'))
                            .filter(node =>
                                typeof node.position === 'undefined' || node.position == null || (node.position.x === 0 && node.position.y === 0)
                            ).length > 0;
                        console.log(missingPositions ? "POSITIONS ARE MISSING. AUTO DISTRIBUTE" : "POSITIONS ARE SET");
                        if (missingPositions) {
                            let distributedNodes = distributeGraph(loadedModel);
                            distributedNodes.forEach(node => {
                                let modelNode = loadedModel.getNode(node.id);
                                modelNode.setPosition(node.x - node.width / 2, node.y - node.height / 2);
                            });
                        }

                        return loadedModel;
                    });
            });

        }, e => {
            console.log("Error reading " + file.name + ": " + e.message)
        });

}

function dataUrlPrefix(assetFileName) {
    let extension = assetFileName.substring(assetFileName.lastIndexOf('.')).toLowerCase();
    switch (extension) {
        case '.bmp':
            return 'data:image/bmp;base64,';
        case '.png':
            return 'data:image/png;base64,';
        case '.jpg':
        case '.jpeg':
            return 'data:image/jpeg;base64,';
        case '.wav':
            return 'data:audio/x-wav;base64,';
        case '.mp3':
            return 'data:audio/mpeg;base64,';
        case '.ogg':
        case '.oga':
            return 'data:audio/ogg;base64,';
        default:
            return 'data:application/octet-stream;base64,';
    }
}

function getTransitionTargetNode(transition, actionNodes, simplifiedNodes) {
    // Try to find an actual action node
    let actionNode = actionNodes.get(transition.actionNode);
    if (actionNode) {
        while (actionNode.optionsIn.length <= transition.optionIndex) {
            actionNode.addOption();
        }
        let optionPort = actionNode.optionsIn[transition.optionIndex];
        if (transition.optionIndex === -1) {
            optionPort = actionNode.randomOptionIn;
        }
        return optionPort;
    }
    // Otherwise, target must be a simplified node
    else {
        return simplifiedNodes.get(transition.actionNode).fromPort;
    }
}



function nodeSize(node) {
    switch (node.getType()) {
        case 'stage':
            return { width: 185, height: 132 };
        case 'action':
            return { width: 150, height: 77 + 22*node.optionsIn.length };
        case 'cover':
            return { width: 150, height: 132 };
        case 'menu':
            return { width: 250, height: 141 + 74*node.optionsStages.length };
        case 'story':
            return { width: 150, height: 132 };
        default:
            // Unsupported node type
            return {};
    }
}

function distributeGraph(model) {
    let graph = new dagre.graphlib.Graph();
    // Configure graph layout
    graph.setGraph({
        rankdir: 'LR',  // Left-to-right
        marginx: 30,
        marginy: 30
    });
    graph.setDefaultEdgeLabel(() => ({}));

    model.getNodes()
        .forEach(node => {
            graph.setNode(node.getID(), { ...nodeSize(node), id: node.getID() });
        });

    model.getLinks()
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
