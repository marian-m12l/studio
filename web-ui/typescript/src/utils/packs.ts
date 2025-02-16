/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
import { LibraryPack } from '../../@types/pack'

export function sortPacks(packs: LibraryPack[]) {
    return packs.sort((a,b) => {
        // Official packs last, alphabetic order except for missing titles (uuids, last)
        const titleA = (a.packs[0].title && a.packs[0].title.toUpperCase()) || '__'+a.uuid.toUpperCase();
        const titleB = (b.packs[0].title && b.packs[0].title.toUpperCase()) || '__'+b.uuid.toUpperCase();
        const officialA = a.packs[0].official || false;
        const officialB = b.packs[0].official || false;
        if (officialA === officialB) {
            return (titleA < titleB) ? -1 : (titleA > titleB) ? 1 : 0;
        } else {
            return (officialA < officialB) ? -1 : 1;
        }
    });
}

interface Model { 
    title: string;
    getEntryPoint: () => {
        getUuid: () => string
    };
    version: string;
}

export function generateFilename(model: Model) {
    return model.title.replace(/ /g, '_') + '-' + model.getEntryPoint().getUuid() + '-v' + model.version + '.zip';
}
