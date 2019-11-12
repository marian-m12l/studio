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

    let stageNodesPromises = Object.values(diagramModel.nodes)
        .filter(node => node.getType() === 'stage')
        .map(async node => {
            // Store assets as separate files in archive
            let imageFile = null;
            if (node.image) {
                let hash = await hashDataUrl(node.image);
                imageFile = hash + extensionFromDataUrl(node.image);
                if (written.indexOf(imageFile) === -1) {
                    zipAssets.file(imageFile, node.image.substring(node.image.indexOf(',') + 1), {base64: true});
                    written.push(imageFile);
                }
            }
            let audioFile = null;
            if (node.audio) {
                let hash = await hashDataUrl(node.audio);
                audioFile = hash + extensionFromDataUrl(node.audio);
                if (written.indexOf(audioFile) === -1) {
                    zipAssets.file(audioFile, node.audio.substring(node.audio.indexOf(',')+1), {base64: true});
                    written.push(audioFile);
                }
            }
            let okTarget = (node.okPort && node.okPort.getLinks() && Object.values(node.okPort.getLinks()).length > 0) ? Object.values(node.okPort.getLinks())[0].getTargetPort() : null;
            let homeTarget = (node.homePort && node.homePort.getLinks() && Object.values(node.homePort.getLinks()).length > 0) ? Object.values(node.homePort.getLinks())[0].getTargetPort() : null;
            let stage = {
                uuid: node.uuid,
                name: node.name,
                position: {
                    x: node.x,
                    y: node.y
                },
                image: imageFile,
                audio: audioFile,
                okTransition: okTarget
                    ? {
                        actionNode: okTarget.getParent().getID(),   // Action nodes referenced by "technical" id
                        optionIndex: (okTarget === okTarget.getParent().randomOptionIn) ? -1 : okTarget.getParent().optionsIn.indexOf(okTarget)
                    }
                    : null,
                homeTransition: homeTarget
                    ? {
                        actionNode: homeTarget.getParent().getID(),    // Action nodes referenced by "technical" id
                        optionIndex: (homeTarget === homeTarget.getParent().randomOptionIn) ? -1 : homeTarget.getParent().optionsIn.indexOf(homeTarget)
                    }
                    : null,
                controlSettings: node.controls
            };
            if (node.squareOne) {
                stage.squareOne = true;
            }
            return stage;
        });
    let stageNodes = await Promise.all(stageNodesPromises);

    let actionNodes = Object.values(diagramModel.nodes)
        .filter(node => node.getType() === 'action')
        .map(node => {
            return {
                id: node.getID(),
                name: node.name,
                position: {
                    x: node.x,
                    y: node.y
                },
                options: node.optionsOut
                    .map(optionPort => {
                        return (optionPort && optionPort.getLinks() && Object.values(optionPort.getLinks()).length > 0)
                            ? Object.values(optionPort.getLinks())[0].getTargetPort().getParent().uuid
                            : null;
                    })   // Stage nodes referenced by "business" uuid
            };
        });

    if (diagramModel.thumbnail) {
        zip.file('thumbnail.png', diagramModel.thumbnail.substring(diagramModel.thumbnail.indexOf(',')+1), {base64: true});
    }

    let storyJson = {
        format: 'v1',
        title: diagramModel.title,
        version: diagramModel.version,
        description: diagramModel.description,
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
