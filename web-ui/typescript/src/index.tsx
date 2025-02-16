/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {Suspense} from 'react';
import ReactDOM from 'react-dom/client';
import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux';
import thunkMiddleware from 'redux-thunk';

import App from './App';
import rootReducer from './reducers';
import './i18n';
import './index.css';



const store = configureStore({
    reducer: rootReducer,
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware({
        thunk: {
          extraArgument: thunkMiddleware,
        },
        serializableCheck: false,
      }),
  })

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);


root.render(
    <Provider store={store}>
        <Suspense fallback="Loading...">
            <App />
        </Suspense>
    </Provider>,
);
