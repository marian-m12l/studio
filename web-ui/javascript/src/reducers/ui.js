/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

const initialState = {
    shown: null
};

const ui = (state = initialState, action) => {
    switch (action.type) {
        case 'SHOW_LIBRARY':
            return { ...state, shown: 'library' };
        case 'SHOW_EDITOR':
            return { ...state, shown: 'editor' };
        default:
            return state
    }
};

export default ui;
