/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { FsDeviceInfos } from "../../@types/device";
import { Pack } from "../../@types/pack";

const initialState = {
    metadata: null,
    packs: []
};

const device = (state = initialState, action: { type: string; metadata: FsDeviceInfos; packs: Pack[]; }) => {
    switch (action.type) {
        case 'DEVICE_PLUGGED':
            return { ...state, metadata: action.metadata };
        case 'DEVICE_UNPLUGGED':
            return { ...state, metadata: null, packs: [] };
        case 'SET_DEVICE_PACKS':
            return { ...state, packs: action.packs };
        default:
            return state
    }
};

export default device;
