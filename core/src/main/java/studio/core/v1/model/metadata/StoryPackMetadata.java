/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.metadata;

import lombok.Data;
import studio.core.v1.utils.PackFormat;

@Data
public class StoryPackMetadata {

    private final PackFormat format;
    private String uuid;
    private short version;
    private String title;
    private String description;
    private byte[] thumbnail;
    private Integer sectorSize;
    private boolean nightModeAvailable = false;

    public StoryPackMetadata(PackFormat format) {
        this.format = format;
    }
}
