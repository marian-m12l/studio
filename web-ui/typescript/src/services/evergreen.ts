/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {handleJsonOrError} from "../utils/fetch";

export const fetchEvergreenInfos = async () => {
    const response = await fetch('http://localhost:8080/api/evergreen/infos');
    return handleJsonOrError(response);
};

export const fetchEvergreenLatestRelease = async () => {
    const response = await fetch('http://localhost:8080/api/evergreen/latest');
    return handleJsonOrError(response);
};

export const fetchEvergreenAnnounce = async () => {
    const response = await fetch('http://localhost:8080/api/evergreen/announce');
    return handleJsonOrError(response);
};
