/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.model;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.driver.model.MetaPackDTO;

public interface LibraryDTOs {

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
        private UUID uuid;
        private List<MetaPackDTO> packs;
    }
}
