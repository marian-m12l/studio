/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {handleJsonOrError} from "../utils/fetch";

export const fetchDeviceInfos = () => {
    return fetch('http://localhost:8080/api/device/infos')
        .then(handleJsonOrError);
};

export const fetchDevicePacks = () => {
    return fetch('http://localhost:8080/api/device/packs')
        .then(handleJsonOrError);
};

export const addFromLibrary = (uuid, path) => {
    return fetch('http://localhost:8080/api/device/addFromLibrary', {
        method: "POST",
        headers: { "Content-Type" : "application/json" },
        body: JSON.stringify({uuid, path})
    })
        .then(handleJsonOrError);
};

export const removeFromDevice = (uuid) => {
    return fetch('http://localhost:8080/api/device/removeFromDevice', {
        method: "POST",
        headers: { "Content-Type" : "application/json" },
        body: JSON.stringify({uuid})
    })
        .then(handleJsonOrError);
};

export const reorderPacks = (uuids) => {
    return fetch('http://localhost:8080/api/device/reorder', {
        method: "POST",
        headers: { "Content-Type" : "application/json" },
        body: JSON.stringify({uuids})
    })
        .then(handleJsonOrError);
};

export const addToLibrary = (uuid, driver) => {
    return fetch('http://localhost:8080/api/device/addToLibrary', {
        method: "POST",
        headers: { "Content-Type" : "application/json" },
        body: JSON.stringify({uuid, driver})
    })
        .then(handleJsonOrError);
};
