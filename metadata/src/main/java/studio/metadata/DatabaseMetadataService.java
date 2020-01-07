/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata;

import com.google.gson.*;
import studio.metadata.logger.PluggableLogger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseMetadataService {

    public static final String OFFICIAL_DB_PROP = "studio.db.official";
    public static final String OFFICIAL_DB_JSON_PATH = "/.studio/db/official.json";
    public static final String THUMBNAILS_STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";
    public static final String UNOFFICIAL_DB_PROP = "studio.db.unofficial";
    public static final String UNOFFICIAL_DB_JSON_PATH = "/.studio/db/unofficial.json";

    private final PluggableLogger logger;

    public DatabaseMetadataService(PluggableLogger pluggableLogger, boolean cleanOnStartup) {
        this.logger = pluggableLogger;
        // Initialize empty unofficial database if needed
        String databasePath = System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH);
        File unofficialDatabase = new File(databasePath);
        if (!unofficialDatabase.exists() || !unofficialDatabase.isFile()) {
            try {
                FileWriter fileWriter = new FileWriter(databasePath);
                fileWriter.write("{}");
                fileWriter.close();
            } catch (IOException e) {
                logger.error("Failed to initialize unofficial metadata database", e);
                throw new IllegalStateException("Failed to initialize unofficial metadata database");
            }
        } else if (cleanOnStartup) {
            // Otherwise clear unofficial database from official packs metadata
            this.cleanUnofficialDatabase();
        }
    }

    public Optional<DatabasePackMetadata> getPackMetadata(String uuid) {
        logger.debug("Fetching metadata for pack: " + uuid);
        Optional<DatabasePackMetadata> metadata = this.getOfficialMetadata(uuid);
        if (!metadata.isPresent()) {
            metadata = this.getUnofficialMetadata(uuid);
        }
        return metadata;
    }

    public Optional<DatabasePackMetadata> getOfficialMetadata(String uuid) {
        try {
            logger.debug("Fetching metadata from official database for pack: " + uuid);
            // Fetch from official metadata database file (path may be overridden by system property `studio.db.official`)
            String databasePath = System.getProperty(OFFICIAL_DB_PROP, System.getProperty("user.home") + OFFICIAL_DB_JSON_PATH);
            JsonObject officialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();
            Optional<String> maybePackKey = officialRoot.keySet().stream()
                    .filter(key -> officialRoot.getAsJsonObject(key).get("uuid").getAsString().equalsIgnoreCase(uuid))
                    .findFirst();
            return maybePackKey.map(packKey -> {
                JsonObject packMetadata = officialRoot.getAsJsonObject(packKey);
                // FIXME Handle multiple locales
                JsonObject localesAvailable = packMetadata.getAsJsonObject("locales_available");
                String locale = localesAvailable.keySet().contains("fr_FR") ? "fr_FR" : localesAvailable.keySet().stream().findFirst().get();
                JsonObject localizedInfos = packMetadata.getAsJsonObject("localized_infos").getAsJsonObject(locale);
                return new DatabasePackMetadata(
                        uuid,
                        localizedInfos.get("title").getAsString(),
                        localizedInfos.get("description").getAsString(),
                        THUMBNAILS_STORAGE_ROOT + localizedInfos.getAsJsonObject("image").get("image_url").getAsString(),
                        true
                );
            });
        } catch (FileNotFoundException e) {
            logger.error("Missing official metadata database file", e);
        }

        // Missing metadata
        return Optional.empty();
    }

    public boolean isOfficialPack(String uuid) {
        try {
            logger.debug("Looking in official database for pack: " + uuid);
            // Look for uuid in official metadata database file
            String databasePath = System.getProperty(OFFICIAL_DB_PROP, System.getProperty("user.home") + OFFICIAL_DB_JSON_PATH);
            JsonObject officialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();
            Optional<String> maybePackKey = officialRoot.keySet().stream()
                    .filter(key -> officialRoot.getAsJsonObject(key).get("uuid").getAsString().equalsIgnoreCase(uuid))
                    .findFirst();
            return maybePackKey.isPresent();
        } catch (FileNotFoundException e) {
            logger.error("Missing official metadata database file", e);
        }

        return false;
    }

    public Optional<DatabasePackMetadata> getUnofficialMetadata(String uuid) {
        logger.debug("Fetching metadata from unofficial database for pack: " + uuid);
        // Fetch from unofficial metadata database file (path may be overridden by system property `studio.db.unofficial`)
        try {
            String databasePath = System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH);
            JsonObject unofficialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();
            if (unofficialRoot.has(uuid)) {
                JsonObject packMetadata = unofficialRoot.getAsJsonObject(uuid);
                return Optional.of(new DatabasePackMetadata(
                        uuid,
                        Optional.ofNullable(packMetadata.get("title")).map(JsonElement::getAsString).orElse(null),
                        Optional.ofNullable(packMetadata.get("description")).map(JsonElement::getAsString).orElse(null),
                        Optional.ofNullable(packMetadata.get("image")).map(JsonElement::getAsString).orElse(null),
                        false
                ));
            }
        } catch (FileNotFoundException e) {
            logger.error("Missing unofficial metadata database file", e);
        }

        // Missing metadata
        return Optional.empty();
    }

    public void replaceOfficialDatabase(JsonObject json) {
        // Update official database
        try {
            String databasePath = System.getProperty(OFFICIAL_DB_PROP, System.getProperty("user.home") + OFFICIAL_DB_JSON_PATH);
            writeDatabaseFile(databasePath, json);
        } catch (IOException e) {
            logger.error("Failed to update official metadata database file", e);
        }
    }

    public void refreshUnofficialMetadata(DatabasePackMetadata meta) {
        // Refresh unofficial database only if the pack isn't an official one
        if (this.getOfficialMetadata(meta.getUuid()).isPresent()) {
            return;
        }
        // Update unofficial database
        try {
            // Open database file
            String databasePath = System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH);
            JsonObject unofficialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();

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
            writeDatabaseFile(databasePath, unofficialRoot);
        } catch (FileNotFoundException e) {
            logger.error("Missing unofficial metadata database file", e);
        } catch (IOException e) {
            logger.error("Failed to update unofficial metadata database file", e);
        }
    }

    public void cleanUnofficialDatabase() {
        logger.debug("Cleaning unofficial database.");
        // Remove official packs from unofficial metadata database file
        try {
            String databasePath = System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH);
            JsonObject unofficialRoot = new JsonParser().parse(new FileReader(databasePath)).getAsJsonObject();
            List<String> toClean = new ArrayList<>();
            for (String uuid : unofficialRoot.keySet()) {
                if (this.isOfficialPack(uuid)) {
                    toClean.add(uuid);
                }
            }
            for (String uuid : toClean) {
                unofficialRoot.remove(uuid);
            }

            // Write database file
            writeDatabaseFile(databasePath, unofficialRoot);
        } catch (FileNotFoundException e) {
            logger.error("Missing unofficial metadata database file", e);
        } catch (IOException e) {
            logger.error("Failed to clean unofficial metadata database file", e);
        }
    }

    private void writeDatabaseFile(String databasePath, JsonObject json) throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        String jsonString = gson.toJson(json);
        FileWriter fileWriter = new FileWriter(databasePath);
        fileWriter.write(jsonString);
        fileWriter.close();
    }

}
