/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from "react";

const initialState = {
    show: false,
    stage: null,
    action: {
        node: null,
        index: null
    }
};

const viewer = (state = initialState, action) => {
    switch (action.type) {
        case 'SHOW_VIEWER':
            return { ...state, show: true };
        case 'HIDE_VIEWER':
            return initialState;
        case 'SET_VIEWER_STAGE':
            return { ...state, stage: action.stage };
        case 'SET_VIEWER_ACTION':
            return {...state, action: action.action };
        default:
            return state
    }
};

export default viewer;
