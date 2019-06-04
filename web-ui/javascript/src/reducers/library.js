/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

const initialState = {
    metadata: null,
    packs: []
};

const library = (state = initialState, action) => {
    switch (action.type) {
        case 'SET_LIBRARY':
            return { ...state, metadata: action.metadata, packs: action.packs };
        default:
            return state
    }
};

export default library;
