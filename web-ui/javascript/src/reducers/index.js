/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { combineReducers } from 'redux';
import device from './device';
import editor from './editor';
import evergreen from './evergreen';
import library from './library';
import settings from './settings';
import ui from './ui';
import viewer from './viewer';


export default combineReducers({
    device,
    editor,
    evergreen,
    library,
    settings,
    ui,
    viewer
});
