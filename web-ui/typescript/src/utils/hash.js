/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

export function hashDataUrl(dataUrl) {
    // Convert data url to byte array
    let bytes = Uint8Array.from(
        atob(dataUrl.substring(dataUrl.indexOf(',') + 1)),
        c => c.charCodeAt(0)
    );
    // Compute sha1 hash and convert to hex string
    return window.crypto.subtle.digest('SHA-1', bytes)
        .then(hash => hexString(hash));
}

function hexString(buffer) {
    const byteArray = new Uint8Array(buffer);

    const hexCodes = [...byteArray].map(value => {
        const hexCode = value.toString(16);
        const paddedHexCode = hexCode.padStart(2, '0');
        return paddedHexCode;
    });

    return hexCodes.join('');
}