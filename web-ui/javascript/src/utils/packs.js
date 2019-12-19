/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

export function sortPacks(packs) {
    return packs.sort((a,b) => {
        // Official packs last, alphabetic order except for missing titles (uuids, last)
        let titleA = a.title && a.title.toUpperCase() || '__'+a.uuid.toUpperCase();
        let titleB = b.title && b.title.toUpperCase() || '__'+b.uuid.toUpperCase();
        if (a.official === b.official) {
            return (titleA < titleB) ? -1 : (titleA > titleB) ? 1 : 0;
        } else {
            return (a.official < b.official) ? -1 : 1;
        }
    });
}

export function generateFilename(model) {
    return model.title.replace(/ /, '_') + '-' + model.getEntryPoint().getUuid() + '-v' + model.version + '.zip';
}
