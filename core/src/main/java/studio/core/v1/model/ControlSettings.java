/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import lombok.Value;

@Value
public class ControlSettings {

    private boolean wheelEnabled;
    private boolean okEnabled;
    private boolean homeEnabled;
    private boolean pauseEnabled;
    private boolean autoJumpEnabled;
}
