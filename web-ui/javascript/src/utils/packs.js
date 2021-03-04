/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

export function sortPacks(packs) {
    return packs.sort((a,b) => {
        // Official packs last, alphabetic order except for missing titles (uuids, last)
        let titleA = (a.packs[0].title && a.packs[0].title.toUpperCase()) || '__'+a.uuid.toUpperCase();
        let titleB = (b.packs[0].title && b.packs[0].title.toUpperCase()) || '__'+b.uuid.toUpperCase();
        let officialA = a.packs[0].official || false;
        let officialB = b.packs[0].official || false;
        if (officialA === officialB) {
            return (titleA < titleB) ? -1 : (titleA > titleB) ? 1 : 0;
        } else {
            return (officialA < officialB) ? -1 : 1;
        }
    });
}

export function generateFilename(model) {
    return model.title.replace(/ /g, '_') + '-' + model.getEntryPoint().getUuid() + '-v' + model.version + '.zip';
}
