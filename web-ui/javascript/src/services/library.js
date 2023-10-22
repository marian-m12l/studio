/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {handleJsonOrError} from "../utils/fetch";

export const fetchLibraryInfos = () => {
    return fetch('/api/library/infos')
        .then(handleJsonOrError);
};

export const fetchLibraryPacks = () => {
    return fetch('/api/library/packs')
        .then(handleJsonOrError);
};

export const downloadFromLibrary = async (uuid, path) => {
    return await fetch('/api/library/download', {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({uuid, path})
    });
};

export const uploadToLibrary = async (uuid, path, packData, progressHandler) => {
    return new Promise((resolve, reject) => {
        let xhr = new XMLHttpRequest();
        if (xhr.upload) {
            xhr.upload.onprogress = progressHandler;
        }
        xhr.onload = () => {
            console.log('xhr upload complete: ' + JSON.parse(xhr.responseText));
            resolve(JSON.parse(xhr.responseText));
        };
        xhr.open('post', '/api/library/upload', true);
        let formData = new FormData();
        formData.append("uuid", uuid);
        formData.append("path", path);
        formData.append("pack", packData);
        xhr.send(formData);
    });
};

export const convertInLibrary = async (uuid, path, format, allowEnriched) => {
    return await fetch('/api/library/convert', {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({uuid, path, format, allowEnriched})
    })
        .then(handleJsonOrError);
};

export const removeFromLibrary = (path) => {
    return fetch('/api/library/remove', {
        method: "POST",
        headers: { "Content-Type" : "application/json" },
        body: JSON.stringify({path})
    })
        .then(handleJsonOrError);
};
