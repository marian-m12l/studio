/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


export const fetchLibraryInfos = () => {
    return fetch('http://localhost:8080/api/library/infos')
        .then(response => response.json());
};

export const fetchLibraryPacks = () => {
    return fetch('http://localhost:8080/api/library/packs')
        .then(response => response.json());
};
