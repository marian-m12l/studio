/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from "react";

const initialState = {
    announceOptOut: localStorage.getItem('announceOptOut') === 'true' || false,
    allowEnriched: localStorage.getItem('allowEnrichedBinaryFormat') === 'true' || false
};

const settings = (state = initialState, action) => {
    switch (action.type) {
        case 'SET_ANNOUNCE_OPTOUT':
            localStorage.setItem('announceOptOut', action.announceOptOut);
            return { ...state, announceOptOut: action.announceOptOut };
        case 'SET_ALLOW_ENRICHED':
            localStorage.setItem('allowEnrichedBinaryFormat', action.allowEnriched);
            return { ...state, allowEnriched: action.allowEnriched };
        default:
            return state
    }
};

export default settings;
