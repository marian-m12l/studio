/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

interface Response<T> {
    ok: boolean;
    text: () => Promise<string>;
    statusText: string;
    json: () => Promise<T>;
}
export async function handleJsonOrError<T>(response: Response<T>) {
    if(!response.ok) {
        const errorText = await response.text();
        return await Promise.reject(
            new Error(response.statusText + ': ' + errorText));
    }
    else {
        return response.json();
    }
}
