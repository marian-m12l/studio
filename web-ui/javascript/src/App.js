/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';

import PackEditor from './components/PackEditor';

import './App.css';


function App() {
    return (
        <div className="App">
            <header className="App-header">
                <p>
                    Welcome to STUdio Web UI.
                </p>
            </header>
            <PackEditor/>
        </div>
    );
}

export default App;
