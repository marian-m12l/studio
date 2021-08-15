/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import JSZip from 'jszip';

import {hashDataUrl} from "./hash";


export async function writeToArchive(diagramModel) {
    // Build zip archive
    var zip = new JSZip();
    var zipAssets = zip.folder('assets');

    // Keep track of written assets
    let written = [];


    // Actual action nodes
    let actionNodes = diagramModel.getNodes()
        .filter(node => node.getType() === 'action')
        .map(node => {
            return {
                id: node.getID(),
                name: node.getName(),
                position: {
                    x: node.getX(),
                    y: node.getY()
                },
                options: node.optionsOut
                    .map(optionPort => {
                        return (optionPort && optionPort.getLinks() && Object.values(optionPort.getLinks()).length > 0)
                            ? Object.values(optionPort.getLinks())[0].getForwardTargetPort().getParent().uuid
                            : null;
                    })   // Stage nodes referenced by "business" uuid
            };
        });
    // Virtual action nodes for 'menu' nodes: 1 for the question and 1 for the options
    actionNodes = actionNodes.concat(
        diagramModel.getNodes()
            .filter(node => node.getType() === 'menu')
            .flatMap(node => {
                // Action node for the question
                let questionActionNode = {
                    id: menuNodeQuestionActionUuid(node),
                    type: node.getType()+".questionaction",
                    groupId: node.getUuid(),
                    name: node.getName()+".questionaction",
                    position: {
                        x: node.getX(),
                        y: node.getY()
                    },
                    options: [menuNodeQuestionStageUuid(node)]
                };
                // Action node for the options
                let optionsActionNode = {
                    id: menuNodeOptionsActionUuid(node),
                    type: node.getType()+".optionsaction",
                    groupId: node.getUuid(),
                    name: node.getName()+".optionsaction",
                    options: node.optionsStages
                        .map((optionStage, idx) => menuNodeOptionStageUuid(node, idx))
                };
                return [questionActionNode, optionsActionNode];
            })
    );
    // Virtual action nodes for 'story' nodes: 1 for each story
    actionNodes = actionNodes.concat(
        diagramModel.getNodes()
            .filter(node => node.getType() === 'story')
            .map((node, idx) => {
                return {
                    id: storyNodeActionUuid(node),
                    type: node.getType()+".storyaction",
                    groupId: node.getUuid(),
                    name: node.getName()+".storyaction",
                    position: {
                        x: node.getX(),
                        y: node.getY()
                    },
                    options: [node.getUuid()]   // Story stage node
                };
            })
    );


    // Actual stage nodes, including 'cover' nodes and 'story' stage nodes
    let stageNodesPromises = diagramModel.getNodes()
        .filter(node => node.getType() === 'stage' || node.getType() === 'cover' || node.getType() === 'story')
        .map(async node => {
            // Store assets as separate files in archive
            let imageFile = null;
            if (node.getImage()) {
                let hash = await hashDataUrl(node.getImage());
                imageFile = hash + extensionFromDataUrl(node.getImage());
                if (written.indexOf(imageFile) === -1) {
                    zipAssets.file(imageFile, node.getImage().substring(node.getImage().indexOf(',') + 1), {base64: true});
                    written.push(imageFile);
                }
            }
            let audioFile = null;
            if (node.getAudio()) {
                let hash = await hashDataUrl(node.getAudio());
                audioFile = hash + extensionFromDataUrl(node.getAudio());
                if (written.indexOf(audioFile) === -1) {
                    zipAssets.file(audioFile, node.getAudio().substring(node.getAudio().indexOf(',')+1), {base64: true});
                    written.push(audioFile);
                }
            }
            let coverNode = diagramModel.getEntryPoint();
            let firstUsefulNode = (coverNode.okPort && coverNode.okPort.getLinks() && Object.values(coverNode.okPort.getLinks()).length > 0) ? Object.values(coverNode.okPort.getLinks())[0].getForwardTargetPort() : null;
            let okTarget = (node.okPort && node.okPort.getLinks() && Object.values(node.okPort.getLinks()).length > 0)
                ? Object.values(node.okPort.getLinks())[0].getForwardTargetPort()
                : node.getType() === 'story'
                    // When no transition is set, story nodes redirect to the first useful node after pack selection
                    ? firstUsefulNode
                    : null;
            let homeTarget = (node.homePort && node.homePort.getLinks() && Object.values(node.homePort.getLinks()).length > 0)
                ? Object.values(node.homePort.getLinks())[0].getForwardTargetPort()
                : (node.getType() === 'story' && !node.disableHome) // Make sure home button is not disabled
                    // When no transition is set, story nodes redirect to the first useful node after pack selection
                    ? firstUsefulNode
                    : null;
            let stage = {
                uuid: node.getUuid(),
                type: node.getType(),
                name: node.getName(),
                position: node.getType() === 'story'
                    ? null      // Story stage node is not positioned
                    : {
                        x: node.getX(),
                        y: node.getY()
                    },
                image: imageFile,
                audio: audioFile,
                okTransition: buildTransitionObject(okTarget),
                homeTransition: buildTransitionObject(homeTarget),
                controlSettings: node.getControls()
            };
            if (node.isSquareOne()) {
                stage.squareOne = true;
            }
            if (node.getType() === 'story') {
                stage.groupId = node.getUuid();
            }
            return stage;
        });
    // Virtual stage nodes for 'menu' nodes: 1 for the question and 1 for each option
    stageNodesPromises = stageNodesPromises.concat(
        diagramModel.getNodes()
            .filter(node => node.getType() === 'menu')
            .map(async node => {
                // Store assets as separate files in archive
                let questionAudioFile = null;
                if (node.getQuestionAudio()) {
                    let hash = await hashDataUrl(node.getQuestionAudio());
                    questionAudioFile = hash + extensionFromDataUrl(node.getQuestionAudio());
                    if (written.indexOf(questionAudioFile) === -1) {
                        zipAssets.file(questionAudioFile, node.getQuestionAudio().substring(node.getQuestionAudio().indexOf(',')+1), {base64: true});
                        written.push(questionAudioFile);
                    }
                }
                // Stage node for the question
                let questionStageNode = {
                    uuid: menuNodeQuestionStageUuid(node),
                    type: node.getType()+".questionstage",
                    groupId: node.getUuid(),
                    name: node.getName(),
                    image: null,
                    audio: questionAudioFile,
                    okTransition: { // OK transitions to the default option's virtual action node
                        actionNode: menuNodeOptionsActionUuid(node),
                        optionIndex: node.getDefaultOption()
                    },
                    homeTransition: null,
                    controlSettings: node.questionStage.controls
                };
                // For each option
                let optionStageNodes = node.optionsStages.map(async (os,idx) => {
                    // Store assets as separate files in archive
                    let imageFile = null;
                    if (os.image) {
                        let hash = await hashDataUrl(os.image);
                        imageFile = hash + extensionFromDataUrl(os.image);
                        if (written.indexOf(imageFile) === -1) {
                            zipAssets.file(imageFile, os.image.substring(os.image.indexOf(',') + 1), {base64: true});
                            written.push(imageFile);
                        }
                    }
                    let audioFile = null;
                    if (os.audio) {
                        let hash = await hashDataUrl(os.audio);
                        audioFile = hash + extensionFromDataUrl(os.audio);
                        if (written.indexOf(audioFile) === -1) {
                            zipAssets.file(audioFile, os.audio.substring(os.audio.indexOf(',')+1), {base64: true});
                            written.push(audioFile);
                        }
                    }
                    // OK transition follows the 'menu' node's output ports
                    let okTarget = (node.optionsOut[idx] && node.optionsOut[idx].getLinks() && Object.values(node.optionsOut[idx].getLinks()).length > 0) ? Object.values(node.optionsOut[idx].getLinks())[0].getForwardTargetPort() : null;
                    // Home transition goes back to the incoming node or to the pack selection
                    let homeTarget = null;
                    let fromLinks = Object.values(node.fromPort.getLinks());
                    if (fromLinks.length !== 1) {
                        // TODO If we cannot determine the incoming node, use default behaviour of going back to pack selection
                        // TODO Add link constraint to limit fromPort to only 1 parent ???
                        homeTarget = null;
                    } else {
                        let incomingNodeSource = fromLinks[0].getForwardSourcePort();
                        let incomingNode = incomingNodeSource.getParent();
                        if (incomingNode.squareOne) {
                            // If incoming node is pack selection, there is no action node to point to, just use default behaviour of going back to pack selection
                            homeTarget = null;
                        } else if (incomingNode.getType() === 'stage') {
                            // Handle incoming node of type stage: lookup for the incoming action and index
                            let prevIncomingLinks = incomingNode.fromPort.getLinks();
                            if (prevIncomingLinks.length !== 1) {
                                // TODO If we cannot determine the incoming action node, use default behaviour of going back to pack selection
                                homeTarget = null;
                            } else {
                                let prevIncomingPort = prevIncomingLinks[0].getForwardSourcePort();
                                let prevAction = prevIncomingPort.getParent();
                                homeTarget = {
                                    actionNode: prevAction.getUuid(),
                                    optionIndex: prevAction.optionsOut.indexOf(prevIncomingPort)
                                };
                            }
                        } else {
                            homeTarget = buildTransitionObject(incomingNodeSource);
                        }
                    }
                    return {
                        uuid: menuNodeOptionStageUuid(node, idx),
                        type: node.getType()+".optionstage",
                        groupId: node.getUuid(),
                        name: node.getOptionName(idx),
                        image: imageFile,
                        audio: audioFile,
                        okTransition: buildTransitionObject(okTarget),
                        homeTransition: homeTarget,
                        controlSettings: os.controls
                    };
                });
                return [questionStageNode].concat(await Promise.all(optionStageNodes));
            })
    );
    // Wait for all assets and virtual stage nodes, then flatten the array
    let stageNodes = await Promise.all(stageNodesPromises);
    stageNodes = stageNodes.flat(1);
    // Make sure entry point is first in nodes list
    let entryPointUuid = diagramModel.getEntryPoint().getUuid();
    stageNodes = stageNodes.reduce((acc, node) => (node.uuid === entryPointUuid) ? [node, ...acc] : [...acc, node], []);

    if (diagramModel.thumbnail) {
        zip.file('thumbnail.png', diagramModel.thumbnail.substring(diagramModel.thumbnail.indexOf(',')+1), {base64: true});
    }

    let storyJson = {
        format: 'v1',
        title: diagramModel.title,
        version: diagramModel.version,
        description: diagramModel.description,
        nightModeAvailable: diagramModel.nightModeAvailable,
        stageNodes,
        actionNodes
    };

    zip.file('story.json', JSON.stringify(storyJson));

    // Promise of Blob
    return zip.generateAsync({type: "blob"});
}

function extensionFromDataUrl(dataUrl) {
    let mimeType = dataUrl.substring(dataUrl.indexOf(':') + 1, dataUrl.indexOf(';base64,'));
    switch (mimeType) {
        case 'image/bmp':
            return '.bmp';
        case 'image/png':
            return '.png';
        case 'image/jpeg':
            return '.jpg';
        case 'audio/wav':
        case 'audio/x-wav':
            return '.wav';
        case 'audio/mp3':
        case 'audio/mpeg':
            return '.mp3';
        case 'audio/ogg':
        case 'video/ogg':
            return '.ogg';
        default:
            return '';
    }
}

function alterUuid(uuid, lastSixBytes) {
    return uuid.substring(0, uuid.length-12) + lastSixBytes;
}

function menuNodeQuestionActionUuid(node) {
    return alterUuid(node.getUuid(), "111111111111");
}

function menuNodeQuestionStageUuid(node) {
    return alterUuid(node.getUuid(), "222222222222");
}

function menuNodeOptionsActionUuid(node) {
    return alterUuid(node.getUuid(), "333333333333");
}

function menuNodeOptionStageUuid(node, optionIndex) {
    return alterUuid(node.getUuid(), "44444444" + optionIndex.toString().padStart(4, '0'));
}

function storyNodeActionUuid(node) {
    return alterUuid(node.getUuid(), "555555555555");
}

function buildTransitionObject(targetPort) {
    if (targetPort === null) {
        return null;
    }
    switch (targetPort.getParent().getType()) {
        case 'action':  // Actual action node
            return {
                actionNode: targetPort.getParent().getID(),   // Action nodes referenced by "technical" id
                optionIndex: (targetPort === targetPort.getParent().randomOptionIn) ? -1 : targetPort.getParent().optionsIn.indexOf(targetPort)
            };
        case 'story':   // Virtual action node for 'story' node
            return {
                actionNode: storyNodeActionUuid(targetPort.getParent()),
                optionIndex: 0
            };
        case 'menu':    // Virtual action node for 'menu' node
            return {
                actionNode: menuNodeQuestionActionUuid(targetPort.getParent()),
                optionIndex: 0
            };
        default:
            return null;
    }
}
