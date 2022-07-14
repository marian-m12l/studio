/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.model;

import java.nio.file.Path;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.driver.model.MetaPackDTO;

public interface LibraryDTOs {

    @Getter
    @AllArgsConstructor
    final class LibraryPackDTO {
        private Path path;
        private long timestamp;
        private StoryPackMetadata metadata;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    final class TransferDTO {
        private String transferId;
    }

    @Getter
    @AllArgsConstructor
    final class SuccessDTO {
        private boolean success;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    final class PathDTO {
        private String path;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    final class SuccessPathDTO {
        private boolean success;
        private String path;
    }

    @Getter
    @Setter
    final class PackDTO {
        private String path;
        private boolean allowEnriched;
        private String format;
    }

    @Getter
    @AllArgsConstructor
    final class UuidPacksDTO {
        private String uuid;
        private List<MetaPackDTO> packs;
    }
}
