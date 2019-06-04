/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


export const fetchDeviceInfos = () => {
    return fetch('http://localhost:8080/api/device/infos')
        .then(response => response.json());
};

export const fetchDevicePacks = () => {
    return fetch('http://localhost:8080/api/device/packs')
        .then(response => response.json());
};
