/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {
    LOCAL_STORAGE_ANNOUNCE_OPTOUT,
    LOCAL_STORAGE_ALLOW_ENRICHED_BINARY_FORMAT
} from "../utils/storage";


const initialState = {
    announceOptOut: localStorage.getItem(LOCAL_STORAGE_ANNOUNCE_OPTOUT) === 'true' || false,
    allowEnriched: localStorage.getItem(LOCAL_STORAGE_ALLOW_ENRICHED_BINARY_FORMAT) === 'true' || false
};

const settings = (state = initialState, action) => {
    switch (action.type) {
        case 'SET_ANNOUNCE_OPTOUT':
            localStorage.setItem(LOCAL_STORAGE_ANNOUNCE_OPTOUT, action.announceOptOut);
            return { ...state, announceOptOut: action.announceOptOut };
        case 'SET_ALLOW_ENRICHED':
            localStorage.setItem(LOCAL_STORAGE_ALLOW_ENRICHED_BINARY_FORMAT, action.allowEnriched);
            return { ...state, allowEnriched: action.allowEnriched };
        default:
            return state
    }
};

export default settings;
