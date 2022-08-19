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
//@JsonInclude(Include.NON_NULL)
public final class MetaPackDTO {
    private String format; // PackFormat (in lowercase)
    private String uuid;
    private short version;
    private String path; // relative path
    private long timestamp;
    private boolean nightModeAvailable;
    private String title;
    private String description;
    private String image; // thumbnail in base64
    private Integer sectorSize;
    private boolean official;

    private String folderName; // FS only
    private long sizeInBytes; // FS only
}
