/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from "react";

const initialState = {
    diagram: null,
    libraryPath: null
};

const editor = (state = initialState, action) => {
    switch (action.type) {
        case 'SET_EDITOR_DIAGRAM':
            return { ...state, diagram: action.diagram, libraryPath: (action.libraryPath || null) };
        default:
            return state
    }
};

export default editor;
