/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import studio.core.v1.service.PackFormat;

@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "thumbnail")
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoryPackMetadata {

    private PackFormat packFormat;
    private String format = "v1";
    private String uuid;
    private short version;
    private String title;
    private String description;
    private byte[] thumbnail;
    private Integer sectorSize;
    private boolean nightModeAvailable;
    // 1st stageNode uuid
    private String uuidFirst;

    // Extract nested : stageNodes[0].uuid
    @JsonProperty("stageNodes")
    private void unpackUuidFirst(JsonNode stageNodes) {
        uuidFirst = stageNodes.get(0).get("uuid").asText();
    }

    public StoryPackMetadata(PackFormat packFormat) {
        this.packFormat = packFormat;
    }
}
