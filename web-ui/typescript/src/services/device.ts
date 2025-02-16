/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {handleJsonOrError} from "../utils/fetch";

export const fetchDeviceInfos = async () => {
    const response = await fetch('http://localhost:8080/api/device/infos');
    return handleJsonOrError(response);
};

export const fetchDevicePacks = async () => {
    const response = await fetch('http://localhost:8080/api/device/packs');
    return handleJsonOrError(response);
};

export const addFromLibrary = async (uuid:string, path:string) => {
    const response = await fetch('http://localhost:8080/api/device/addFromLibrary', {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uuid, path })
    });
    return handleJsonOrError(response);
};

export const removeFromDevice = async (uuid:string) => {
    const response = await fetch('http://localhost:8080/api/device/removeFromDevice', {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uuid })
    });
    return handleJsonOrError(response);
};

export const reorderPacks = async (uuids:string[]) => {
    const response = await fetch('http://localhost:8080/api/device/reorder', {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uuids })
    });
    return handleJsonOrError(response);
};

export const addToLibrary = async (uuid:string, driver) => {
    const response = await fetch('http://localhost:8080/api/device/addToLibrary', {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uuid, driver })
    });
    return handleJsonOrError(response);
};
