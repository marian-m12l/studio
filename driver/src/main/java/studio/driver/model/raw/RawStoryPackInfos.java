/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model.raw;

import lombok.Data;
import lombok.EqualsAndHashCode;
import studio.driver.model.StoryPackInfos;

@Data
@EqualsAndHashCode(callSuper = true)
public class RawStoryPackInfos extends StoryPackInfos {
    private int startSector;
    private int sizeInSectors;
    private short statsOffset;
    private short samplingRate;
}
