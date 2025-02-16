/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

const initialState = {
    show: false,
    stage: null,
    action: {
        node: null,
        index: null
    },
    diagram: null,
    options: {
        translucent: false,
        overlay: true,
        autoplay: true
    }
};

const viewer = (state = initialState, action) => {
    switch (action.type) {
        case 'SHOW_VIEWER':
            return { ...state, show: true };
        case 'HIDE_VIEWER':
            // Keep options when hiding the viewer
            return {...initialState, options: state.options};
        case 'SET_VIEWER_DIAGRAM':
            return { ...state, diagram: action.diagram };
        case 'SET_VIEWER_STAGE':
            return { ...state, stage: action.stage };
        case 'SET_VIEWER_ACTION':
            return {...state, action: action.action };
        case 'SET_VIEWER_OPTIONS':
            return {...state, options: action.options };
        default:
            return state
    }
};

export default viewer;
