/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public class DatabaseMetadataService {

    private static final Logger LOGGER = Logger.getLogger(DatabaseMetadataService.class.getName());

    public static final String OFFICIAL_DB_PROP = "studio.db.official";
    public static final String OFFICIAL_DB_JSON_PATH = "/.studio/db/official.json";
    public static final String THUMBNAILS_STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";
    public static final String LUNII_GUEST_TOKEN_URL = "https://server-auth-prod.lunii.com/guest/create";
    public static final String LUNII_PACKS_DATABASE_URL = "https://server-data-prod.lunii.com/v2/packs";
    public static final String UNOFFICIAL_DB_PROP = "studio.db.unofficial";
    public static final String UNOFFICIAL_DB_JSON_PATH = "/.studio/db/unofficial.json";

    private final Map<String, JsonObject> cachedOfficialDatabase;

    public DatabaseMetadataService(boolean isAgent) {
        // Read and cache official database
        cachedOfficialDatabase = new HashMap<>();
        if (!isAgent) {
            try {
                LOGGER.fine("Reading and caching official metadata database");
                // Read official metadata database file (path may be overridden by system property `studio.db.official`)
                Path databasePath = officialDbPath();
                String jsonString = Files.readString(databasePath);
                JsonObject officialRoot = new JsonParser().parse(jsonString).getAsJsonObject();   // throws IllegalStateException
                // Support newer file format which has an additional wrapper: { "code": "0.0", "response": { ...
                final JsonObject packsRoot = (officialRoot.keySet().contains("response")) ? officialRoot.getAsJsonObject("response") : officialRoot;
                if (packsRoot == null) {
                    throw new IllegalStateException("Failed to get json root node");
                }
                // Go through all packs
                packsRoot.keySet().forEach(key -> {
                    JsonObject packMetadata = packsRoot.getAsJsonObject(key);
                    String uuid = packMetadata.get("uuid").getAsString();
                    cachedOfficialDatabase.put(uuid, packMetadata);
                });
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Missing official metadata database file", e);
                this.fetchOfficialDatabase();
            } catch (JsonParseException|IllegalStateException e) {
                // Graceful failure on invalid file content
                LOGGER.log(Level.WARNING, "Official metadata database file is invalid", e);
                this.fetchOfficialDatabase();
            }
        }
        // Initialize empty unofficial database if needed
        Path databasePath = unofficialDbPath();
        if (Files.notExists(databasePath) || !Files.isRegularFile(databasePath)) {
            try {
                Files.writeString(databasePath, "{}");
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to initialize unofficial metadata database", e);
                throw new IllegalStateException("Failed to initialize unofficial metadata database");
            }
        } else if (!isAgent) {
            // Otherwise clear unofficial database from official packs metadata
            this.cleanUnofficialDatabase();
        }
    }

    public Optional<DatabasePackMetadata> getPackMetadata(String uuid) {
        LOGGER.fine("Fetching metadata for pack: " + uuid);
        Optional<DatabasePackMetadata> metadata = this.getOfficialMetadata(uuid);
        if (!metadata.isPresent()) {
            metadata = this.getUnofficialMetadata(uuid);
        }
        return metadata;
    }

    public Optional<DatabasePackMetadata> getOfficialMetadata(String uuid) {
        LOGGER.fine("Fetching metadata from official database for pack: " + uuid);
        return Optional.ofNullable(cachedOfficialDatabase.get(uuid)).map(packMetadata -> {
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
    }

    public boolean isOfficialPack(String uuid) {
        LOGGER.fine("Looking in official database for pack: " + uuid);
        return cachedOfficialDatabase.containsKey(uuid);
    }

    public synchronized Optional<DatabasePackMetadata> getUnofficialMetadata(String uuid) {
        LOGGER.fine("Fetching metadata from unofficial database for pack: " + uuid);
        // Fetch from unofficial metadata database file (path may be overridden by system property `studio.db.unofficial`)
        Path databasePath = unofficialDbPath();
        try {
            String jsonString = Files.readString(databasePath);
            JsonObject unofficialRoot = new JsonParser().parse(jsonString).getAsJsonObject();
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
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Missing unofficial metadata database file", e);
        }

        // Missing metadata
        return Optional.empty();
    }

    private void replaceOfficialDatabase(JsonObject json) {
        // Update official database
        Path databasePath = officialDbPath();
        try {
            writeDatabaseFile(databasePath, json);

            // Go through all packs to update cache
            json.keySet().forEach(key -> {
                JsonObject packMetadata = json.getAsJsonObject(key);
                String uuid = packMetadata.get("uuid").getAsString();
                cachedOfficialDatabase.put(uuid, packMetadata);
            });
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to update official metadata database file", e);
        }
    }

    private void fetchOfficialDatabase() {
        try {
            LOGGER.fine("Fetching official metadata database");
            // Get a guest token
            URL tokenUrl = new URL(LUNII_GUEST_TOKEN_URL);
            HttpURLConnection tokenConnection = (HttpURLConnection) tokenUrl.openConnection();
            tokenConnection.setRequestMethod("GET");
            tokenConnection.setConnectTimeout(10000);
            tokenConnection.setReadTimeout(10000);
            int tokenStatusCode = tokenConnection.getResponseCode();
            if (tokenStatusCode != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch guest token. Status code: " + tokenStatusCode);
                return;
            }
            JsonParser parser = new JsonParser();
            String token;
            // OK, read response body
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(tokenConnection.getInputStream(), StandardCharsets.UTF_8))) {
                // Extract token from response body
                JsonObject tokenJson = parser.parse(new JsonReader(br)).getAsJsonObject();
                token = tokenJson.getAsJsonObject("response").getAsJsonObject("token").get("server").getAsString();
            }
            LOGGER.fine("Guest token: " + token);

            // Call service to fetch metadata for all packs
            URL url = new URL(LUNII_PACKS_DATABASE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-AUTH-TOKEN", token);
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch official metadata database. Status code: " + statusCode);
                return;
            }
            // OK, read response body
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                // Extract metadata database from response body
                JsonObject json = parser.parse(new JsonReader(br)).getAsJsonObject();
                JsonObject response = json.get("response").getAsJsonObject();
                // Try and update official database
                LOGGER.info("Fetched metadata, updating local database");
                this.replaceOfficialDatabase(response);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch official metadata database.", e);
        }
    }

    public void refreshUnofficialMetadata(DatabasePackMetadata meta) {
        // Refresh unofficial database only if the pack isn't an official one
        if (this.getOfficialMetadata(meta.getUuid()).isPresent()) {
            return;
        }
        // Update unofficial database
        Path databasePath = unofficialDbPath();
        try {
            // Open database file
            String jsonString = Files.readString(databasePath);
            JsonObject unofficialRoot = new JsonParser().parse(jsonString).getAsJsonObject();

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
            LOGGER.log(Level.SEVERE, "Missing unofficial metadata database file", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to update unofficial metadata database file", e);
        }
    }

    public void cleanUnofficialDatabase() {
        LOGGER.fine("Cleaning unofficial database.");
        // Remove official packs from unofficial metadata database file
        Path databasePath = unofficialDbPath();
        try  {
            String jsonString = Files.readString(databasePath);
            JsonObject unofficialRoot = new JsonParser().parse(jsonString).getAsJsonObject();
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
            LOGGER.log(Level.SEVERE, "Missing unofficial metadata database file", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to clean unofficial metadata database file", e);
        }
    }

    private synchronized void writeDatabaseFile(Path databasePath, JsonObject json) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(databasePath, gson.toJson(json));
    }

    public static Path officialDbPath() {
        return Path.of(System.getProperty(OFFICIAL_DB_PROP, System.getProperty("user.home") + OFFICIAL_DB_JSON_PATH));
    }

    public static Path unofficialDbPath() {
        return Path.of(System.getProperty(UNOFFICIAL_DB_PROP, System.getProperty("user.home") + UNOFFICIAL_DB_JSON_PATH));
    }

}
