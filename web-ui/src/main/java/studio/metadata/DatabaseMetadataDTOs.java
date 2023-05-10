/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.metadata;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public interface DatabaseMetadataDTOs {

    String THUMBNAILS_STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";

    @RegisterRestClient(baseUri = "https://server-auth-prod.lunii.com/guest")
    interface LuniiGuestClient {
        @GET
        @Path("create")
        TokenResponse auth();
    }

    @RegisterRestClient(baseUri = "https://server-data-prod.lunii.com/v2")
    interface LuniiPacksClient {
        @GET
        @Path("packs")
        PacksResponse packs(@HeaderParam("X-AUTH-TOKEN") String token);
    }

    @RegisterForReflection
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    final class DatabasePackMetadata {
        private UUID uuid;
        private String title;
        private String description;
        private String thumbnail;
        private boolean official;
    }

    @Getter
    final class TokenResponse {
        private String token;

        // Extract nested : response.token.server
        @JsonProperty("response")
        private void unpackToken(JsonNode response) {
            token = response.get("token").get("server").asText();
        }
    }

    @Getter
    @Setter
    final class PacksResponse {
        private Map<String, OfficialPack> response;

        @Getter
        @Setter
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        static final class OfficialPack {
            private UUID uuid;
            private Map<Locale, Boolean> localesAvailable;
            private Map<Locale, Infos> localizedInfos;

            @Getter
            @Setter
            static final class Infos {
                private String title;
                private String description;
                private String thumbnail;

                // Extract nested : image.image_url
                @JsonProperty("image")
                private void unpackImageUrl(Map<String, String> image) {
                    thumbnail = THUMBNAILS_STORAGE_ROOT + image.get("image_url");
                }
            }
        }
    }
}
