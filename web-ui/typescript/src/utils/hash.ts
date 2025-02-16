/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

export async function hashDataUrl(dataUrl: string) {
    // Convert data url to byte array
    const bytes = Uint8Array.from(
        atob(dataUrl.substring(dataUrl.indexOf(',') + 1)),
        c => c.charCodeAt(0)
    );
    // Compute sha1 hash and convert to hex string
    const hash = await window.crypto.subtle.digest('SHA-1', bytes);
    return hexString(hash);
}

function hexString(buffer: ArrayBuffer): string {
    const byteArray: Uint8Array = new Uint8Array(buffer);

    const hexCodes: string[] = [...byteArray].map(value => {
        const hexCode: string = value.toString(16);
        const paddedHexCode: string = hexCode.padStart(2, '0');
        return paddedHexCode;
    });

    return hexCodes.join('');
}