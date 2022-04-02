/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {handleJsonOrError} from "../utils/fetch";

export const fetchEvergreenInfos = () => {
    return fetch('/api/evergreen/infos')
        .then(handleJsonOrError);
};

export const fetchEvergreenLatestRelease = () => {
    return fetch('/api/evergreen/latest')
        .then(handleJsonOrError);
};

export const fetchEvergreenAnnounce = () => {
    return fetch('/api/evergreen/announce')
        .then(handleJsonOrError);
};
