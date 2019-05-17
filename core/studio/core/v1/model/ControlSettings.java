/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

public class ControlSettings {

    private boolean wheelEnabled;
    private boolean okEnabled;
    private boolean homeEnabled;
    private boolean pauseEnabled;
    private boolean autoJumpEnabled;

    public ControlSettings() {
    }

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

    public void setWheelEnabled(boolean wheelEnabled) {
        this.wheelEnabled = wheelEnabled;
    }

    public boolean isOkEnabled() {
        return okEnabled;
    }

    public void setOkEnabled(boolean okEnabled) {
        this.okEnabled = okEnabled;
    }

    public boolean isHomeEnabled() {
        return homeEnabled;
    }

    public void setHomeEnabled(boolean homeEnabled) {
        this.homeEnabled = homeEnabled;
    }

    public boolean isPauseEnabled() {
        return pauseEnabled;
    }

    public void setPauseEnabled(boolean pauseEnabled) {
        this.pauseEnabled = pauseEnabled;
    }

    public boolean isAutoJumpEnabled() {
        return autoJumpEnabled;
    }

    public void setAutoJumpEnabled(boolean autoJumpEnabled) {
        this.autoJumpEnabled = autoJumpEnabled;
    }
}
