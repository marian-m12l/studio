/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractDeviceInfos {
    private short firmwareMajor;
    private short firmwareMinor;
    private String serialNumber;
}