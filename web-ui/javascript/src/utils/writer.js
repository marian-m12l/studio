/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import JSZip from 'jszip';


export function writeToArchive(diagramModel) {
    // Build zip archive
    var zip = new JSZip();
    var zipAssets = zip.folder('assets');

    let stageNodes = Object.values(diagramModel.nodes)
        .filter(node => node.getType() === 'stage')
        .map(node => {
            // Store assets as separate files in archive
            let imageFile = null;
            if (node.image) {
                imageFile = node.uuid + '.bmp';
                zipAssets.file(imageFile, node.image.substring(node.image.indexOf(',')+1), {base64: true});
            }
            let audioFile = null;
            if (node.audio) {
                audioFile = node.uuid + '.wav';
                zipAssets.file(audioFile, node.audio.substring(node.audio.indexOf(',')+1), {base64: true});
            }
            let okTarget = (node.okPort && node.okPort.getLinks() && Object.values(node.okPort.getLinks()).length > 0) ? Object.values(node.okPort.getLinks())[0].getTargetPort() : null;
            let homeTarget = (node.homePort && node.homePort.getLinks() && Object.values(node.homePort.getLinks()).length > 0) ? Object.values(node.homePort.getLinks())[0].getTargetPort() : null;
            return {
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
                        optionIndex: okTarget.getParent().optionsIn.indexOf(okTarget)
                    }
                    : null,
                homeTransition: homeTarget
                    ? {
                        actionNode: homeTarget.getParent().getID(),    // Action nodes referenced by "technical" id
                        optionIndex: homeTarget.getParent().optionsIn.indexOf(homeTarget)
                    }
                    : null,
                controlSettings: node.controls
            };
        });

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

    let storyJson = {
        format: 'v1',
        title: diagramModel.title,
        version: diagramModel.version,
        stageNodes,
        actionNodes
    };

    zip.file('story.json', JSON.stringify(storyJson));

    // Promise of Blob
    return zip.generateAsync({type: "blob"});
}