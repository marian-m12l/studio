/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


export const fetchEvergreenInfos = () => {
    return fetch('http://localhost:8080/api/evergreen/infos')
        .then(response => response.json());
};

export const fetchEvergreenLatestRelease = () => {
    return fetch('http://localhost:8080/api/evergreen/latest')
        .then(response => response.json());
};
