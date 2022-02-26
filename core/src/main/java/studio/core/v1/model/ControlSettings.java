/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

public class ControlSettings {

    private final boolean wheelEnabled;
    private final boolean okEnabled;
    private final boolean homeEnabled;
    private final boolean pauseEnabled;
    private final boolean autoJumpEnabled;

    public ControlSettings(boolean wheelEnabled, boolean okEnabled, boolean homeEnabled, boolean pauseEnabled, boolean autoJumpEnabled) {
        this.wheelEnabled = wheelEnabled;
        this.okEnabled = okEnabled;
        this.homeEnabled = homeEnabled;
        this.pauseEnabled = pauseEnabled;
        this.autoJumpEnabled = autoJumpEnabled;
    }

    public boolean isWheelEnabled() {
        return wheelEnabled;
    }

    public boolean isOkEnabled() {
        return okEnabled;
    }

    public boolean isHomeEnabled() {
        return homeEnabled;
    }

    public boolean isPauseEnabled() {
        return pauseEnabled;
    }

    public boolean isAutoJumpEnabled() {
        return autoJumpEnabled;
    }
}
