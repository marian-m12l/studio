/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.model;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.Value;
import studio.core.v1.model.metadata.StoryPackMetadata;

public class LibraryDTOs {

    @Value
    public static class LibraryPackDTO {
        private Path path;
        private long timestamp;
        private StoryPackMetadata metadata;
    }

    @Value
    public static class TransferDTO {
        private String transferId;
    }

    @Value
    public static class SuccessDTO {
        private boolean success;
    }

    @Data
    public static class PathDTO {
        private String path;
    }

    @Value
    public static class SuccessPathDTO {
        private boolean success;
        private String path;
    }

    @Data
    public static class PackDTO {
        private String path;
        private boolean allowEnriched;
        private String format;
    }

    @Value
    public static class UuidPacksDTO {
        private String uuid;
        private List<MetaPackDTO> packs;
    }

    @Data
    @JsonInclude(Include.NON_NULL)
    public static class MetaPackDTO {
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

        private String folderName; // for device only
        private long sizeInBytes; // for device only
    }
}
