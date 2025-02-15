/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

export function handleJsonOrError(response) {
    if(!response.ok) {
        return response.text()
            .then(errorText => Promise.reject(
                new Error(response.statusText + ': ' + errorText))
            );
    }
    else {
        return response.json();
    }
}
