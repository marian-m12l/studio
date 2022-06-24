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

public interface LibraryDTOs {

    @Value
    static class LibraryPackDTO {
        private Path path;
        private long timestamp;
        private StoryPackMetadata metadata;
    }

    @Value
    static class TransferDTO {
        private String transferId;
    }

    @Value
    static class SuccessDTO {
        private boolean success;
    }

    @Data
    static final class PathDTO {
        private String path;
    }

    @Value
    static class SuccessPathDTO {
        private boolean success;
        private String path;
    }

    @Data
    static final class PackDTO {
        private String path;
        private boolean allowEnriched;
        private String format;
    }

    @Value
    static class UuidPacksDTO {
        private String uuid;
        private List<MetaPackDTO> packs;
    }

    @Data
    @JsonInclude(Include.NON_NULL)
    static final class MetaPackDTO {
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
