/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import com.google.gson.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.webui.model.DatabasePackMetadata;

import java.io.*;
import java.util.Optional;

public class DatabaseMetadataService {

    public static final String OFFICIAL_DB_PROP = "studio.db.official";
    public static final String OFFICIAL_DB_JSON_PATH = "/.studio/db/official.json";
    public static final String THUMBNAILS_STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";
    public static final String UNOFFICIAL_DB_PROP = "studio.db.unofficial";
    public static final String UNOFFICIAL_DB_JSON_PATH = "/.studio/db/unofficial.json";

    private final Logger LOGGER = LoggerFactory.getLogger(DatabaseMetadataService.class);

    public DatabaseMetadataService() {
        // Initialize empty unofficial database if needed
        String databasePath = System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH);
        File unofficialDatabase = new File(databasePath);
        if (!unofficialDatabase.exists() || !unofficialDatabase.isFile()) {
            try {
                FileWriter fileWriter = new FileWriter(databasePath);
                fileWriter.write("{}");
                fileWriter.close();
            } catch (IOException e) {
                LOGGER.error("Failed to initialize unofficial metadata database", e);
                e.printStackTrace();
                throw new IllegalStateException("Failed to initialize unofficial metadata database");
            }
        }
    }

    public Optional<DatabasePackMetadata> getPackMetadata(String uuid) {
        try {
            LOGGER.debug("Fetching metadata from official database for pack: " + uuid);
            // Fetch from official metadata database file (path may be overridden by system property `studio.db.official`)
            String databasePath = System.getProperty(OFFICIAL_DB_PROP, System.getProperty("user.home") + OFFICIAL_DB_JSON_PATH);
            com.google.gson.JsonObject officialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();
            Optional<String> maybePackKey = officialRoot.keySet().stream()
                    .filter(key -> officialRoot.getAsJsonObject(key).get("uuid").getAsString().equalsIgnoreCase(uuid))
                    .findFirst();
            if (maybePackKey.isPresent()) {
                com.google.gson.JsonObject packMetadata = officialRoot.getAsJsonObject(maybePackKey.get());
                // FIXME Handle multiple locales
                JsonObject localesAvailable = packMetadata.getAsJsonObject("locales_available");
                String locale = localesAvailable.keySet().contains("fr_FR") ? "fr_FR" : localesAvailable.keySet().stream().findFirst().get();
                JsonObject localizedInfos = packMetadata.getAsJsonObject("localized_infos").getAsJsonObject(locale);
                return Optional.of(new DatabasePackMetadata(
                        uuid,
                        localizedInfos.get("title").getAsString(),
                        localizedInfos.get("description").getAsString(),
                        THUMBNAILS_STORAGE_ROOT + localizedInfos.getAsJsonObject("image").get("image_url").getAsString(),
                        true
                ));
            } else {
                // Fetch from unofficial metadata database file (path may be overridden by system property `studio.db.unofficial`)
                try {
                    databasePath = System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH);
                    com.google.gson.JsonObject unofficialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();
                    if (unofficialRoot.has(uuid)) {
                        com.google.gson.JsonObject packMetadata = unofficialRoot.getAsJsonObject(uuid);
                        return Optional.of(new DatabasePackMetadata(
                                uuid,
                                Optional.ofNullable(packMetadata.get("title")).map(JsonElement::getAsString).orElse(null),
                                Optional.ofNullable(packMetadata.get("description")).map(JsonElement::getAsString).orElse(null),
                                Optional.ofNullable(packMetadata.get("image")).map(JsonElement::getAsString).orElse(null),
                                false
                        ));
                    }
                } catch (FileNotFoundException e) {
                    LOGGER.error("Missing unofficial metadata database file", e);
                }
            }
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing official metadata database file", e);
        }

        // Missing metadata
        return Optional.empty();
    }

    public void refreshUnofficialMetadata(DatabasePackMetadata meta) {
        // Update unofficial database
        try {
            // Open database file
            String databasePath = System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH);
            com.google.gson.JsonObject unofficialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();

            // Replace or add pack metadata
            JsonObject value = new JsonObject();
            value.addProperty("uuid", meta.getUuid());
            if (meta.getTitle() != null) {
                value.addProperty("title", meta.getTitle());
            }
            if (meta.getDescription() != null) {
                value.addProperty("description", meta.getDescription());
            }
            if (meta.getThumbnail() != null) {
                value.addProperty("image", meta.getThumbnail());
            }
            unofficialRoot.add(meta.getUuid(), value);

            // Write database file
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            String jsonString = gson.toJson(unofficialRoot);
            FileWriter fileWriter = new FileWriter(databasePath);
            fileWriter.write(jsonString);
            fileWriter.close();
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing unofficial metadata database file", e);
        } catch (IOException e) {
            LOGGER.error("Failed to update unofficial metadata database file", e);
        }
    }

}
