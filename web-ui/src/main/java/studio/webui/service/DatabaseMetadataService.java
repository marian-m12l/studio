/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import com.google.gson.JsonParser;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.webui.model.DatabasePackMetadata;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Optional;

public class DatabaseMetadataService {

    public static final String OFFICIAL_DB_PROP = "studio.db.official";
    public static final String OFFICIAL_DB_JSON_PATH = "/.studio/db/official.json";
    public static final String THUMBNAILS_STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";

    private final Logger LOGGER = LoggerFactory.getLogger(DatabaseMetadataService.class);

    public DatabaseMetadataService() {
    }

    public Optional<DatabasePackMetadata> getPackMetadata(String uuid) {
        try {
            LOGGER.debug("Fetching metadata from official database for pack: " + uuid);
            // Fetch from official metadata database file (path may be overridden by system property `studio.db.official`)
            String databasePath = System.getProperty(OFFICIAL_DB_PROP, System.getProperty("user.home") + OFFICIAL_DB_JSON_PATH);
            com.google.gson.JsonObject root = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();
            Optional<String> maybePackKey = root.keySet().stream()
                    .filter(key -> root.getAsJsonObject(key).get("uuid").getAsString().equalsIgnoreCase(uuid))
                    .findFirst();
            if (maybePackKey.isPresent()) {
                com.google.gson.JsonObject packMetadata = root.getAsJsonObject(maybePackKey.get());
                return Optional.of(new DatabasePackMetadata(
                        uuid,
                        packMetadata.get("title").getAsString(),
                        packMetadata.get("description").getAsString(),
                        THUMBNAILS_STORAGE_ROOT + packMetadata.getAsJsonObject("image").get("image_url").getAsString(),
                        true
                ));
            }
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing official metadata database file", e);
        }

        // Missing metadata
        return Optional.empty();
    }

}
