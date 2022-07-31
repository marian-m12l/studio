/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.asset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.core.v1.utils.security.SecurityUtils;

@Getter
@Setter
@EqualsAndHashCode(exclude = "rawData")
public class MediaAsset {

    private MediaAssetType type;
    private byte[] rawData;
    private String name;

    public MediaAsset(MediaAssetType type, byte[] rawData) {
        this.type = type;
        this.rawData = rawData;
        updateName();
    }

    @JsonCreator
    public MediaAsset(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    public void updateName() {
        this.name = SecurityUtils.sha1Hex(rawData) + type.firstExtension();
    }

    private int dotIndex() {
        return name.lastIndexOf(".");
    }

    public String findHash() {
        return name.substring(0, dotIndex()).toLowerCase();
    }

    public String findExtension() {
        return name.substring(dotIndex()).toLowerCase();
    }

    public void guessType() {
        setType(MediaAssetType.fromExtension(findExtension()));
    }
}
