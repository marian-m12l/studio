/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { combineReducers } from 'redux';
import device from './device';
import library from './library';
import viewer from './viewer';


export default combineReducers({
    device,
    library,
    viewer
});
