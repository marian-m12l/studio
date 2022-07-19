/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.asset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.core.v1.utils.security.SecurityUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "rawData")
public class ImageAsset {

    private ImageType type;
    private byte[] rawData;
    private String name;

    public ImageAsset(ImageType type, byte[] rawData) {
        this.type = type;
        this.rawData = rawData;
        updateName();
    }

    @JsonCreator
    public ImageAsset(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    public void updateName() {
        this.name = SecurityUtils.sha1Hex(rawData) + type.getFirstExtension();
    }

}
