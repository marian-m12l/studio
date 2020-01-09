/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


export const fetchWatchdogSupported= () => {
    return fetch('http://localhost:8080/api/watchdog/supported')
        .then(response => response.json());
};

export const fetchWatchdogLatest = () => {
    return fetch('http://localhost:8080/api/watchdog/latest')
        .then(response => response.json());
};
