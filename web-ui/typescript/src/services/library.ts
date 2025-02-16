/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { LibraryPack } from "../../@types/pack";
import {handleJsonOrError} from "../utils/fetch";

export const fetchLibraryInfos = () => {
    return fetch('http://localhost:8080/api/library/infos')
        .then(handleJsonOrError);
};

export const fetchLibraryPacks = () => {
    return fetch('http://localhost:8080/api/library/packs')
        .then(handleJsonOrError);
};

export const downloadFromLibrary = async (uuid:string, path:string) => {
    return await fetch('http://localhost:8080/api/library/download', {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({uuid, path})
    });
};

export const uploadToLibrary = async (uuid: string | Blob, path: string | Blob, packData: string | Blob | LibraryPack, progressHandler: ((this: XMLHttpRequest, ev: ProgressEvent) => any) | null) => {
    return new Promise((resolve) => {
        const xhr = new XMLHttpRequest();
        if (xhr.upload) {
            xhr.upload.onprogress = progressHandler;
        }
        xhr.onload = () => {
            console.log('xhr upload complete: ' + JSON.parse(xhr.responseText));
            resolve(JSON.parse(xhr.responseText));
        };
        xhr.open('post', 'http://localhost:8080/api/library/upload', true);
        const formData = new FormData();
        formData.append("uuid", uuid);
        formData.append("path", path);
        formData.append("pack", packData);
        xhr.send(formData);
    });
};

export const convertInLibrary = async (uuid: string, path: string, format: string, allowEnriched: boolean) => {
    return await fetch('http://localhost:8080/api/library/convert', {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({uuid, path, format, allowEnriched})
    })
        .then(handleJsonOrError);
};

export const removeFromLibrary = (path: string) => {
    return fetch('http://localhost:8080/api/library/remove', {
        method: "POST",
        headers: { "Content-Type" : "application/json" },
        body: JSON.stringify({path})
    })
        .then(handleJsonOrError);
};
