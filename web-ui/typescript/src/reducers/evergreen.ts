/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

const initialState = {
    version: '',
    announce: null
};

const evergreen = (state = initialState, action) => {
    switch (action.type) {
        case 'SET_APPLICATION_VERSION':
            return { ...state, version: action.version };
        case 'SET_ANNOUNCE':
            return { ...state, announce: action.announce };
        default:
            return state
    }
};

export default evergreen;
