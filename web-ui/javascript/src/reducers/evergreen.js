/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from "react";

const initialState = {
    version: ''
};

const evergreen = (state = initialState, action) => {
    switch (action.type) {
        case 'SET_APPLICATION_VERSION':
            return { ...state, version: action.version };
        default:
            return state
    }
};

export default evergreen;
