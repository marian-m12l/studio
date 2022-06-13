/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.raw;

import java.util.UUID;

import lombok.Data;
import lombok.EqualsAndHashCode;
import studio.driver.model.DeviceInfos;

@Data
@EqualsAndHashCode(callSuper = true)
public class RawDeviceInfos extends DeviceInfos {
    private UUID uuid;
    private int sdCardSizeInSectors;
    private int usedSpaceInSectors;
    private boolean inError;
}
