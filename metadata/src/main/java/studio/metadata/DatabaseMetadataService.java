/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import studio.config.StudioConfig;

public class DatabaseMetadataService {

    private static final Logger LOGGER = LogManager.getLogger(DatabaseMetadataService.class);

    public static final String THUMBNAILS_STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";
    public static final String LUNII_GUEST_TOKEN_URL = "https://server-auth-prod.lunii.com/guest/create";
    public static final String LUNII_PACKS_DATABASE_URL = "https://server-data-prod.lunii.com/v2/packs";

    // databases paths
    private Path officialDbPath = officialDbPath();
    private Path unofficialDbPath = unofficialDbPath();

    // databases caches : <uuid, packMetadata>
    private final Map<String, JsonObject> cachedOfficialDatabase = new HashMap<>();
    private final JsonObject unofficialJsonCache;
    private long lastModifiedCache = 0;

    public DatabaseMetadataService(boolean isAgent) {
        if (!isAgent) {
            try {
                // Read official metadata database file
                LOGGER.info("Reading official metadata in {}", officialDbPath);
                String jsonString = Files.readString(officialDbPath);
                JsonObject officialRoot = new JsonParser().parse(jsonString).getAsJsonObject();
                // Support newer file which has an additional wrapper: { "code": "0.0", "response": { ...
                JsonObject packsRoot = officialRoot.has("response") ? officialRoot.getAsJsonObject("response") : officialRoot;
                if (packsRoot == null) {
                    throw new IllegalStateException("Failed to get json root node");
                }
                // Refresh cache
                refreshOfficialCache(packsRoot);
            } catch (IOException | JsonParseException | IllegalStateException e) {
                // Graceful failure on invalid file content
                LOGGER.warn( "Official metadata database file is invalid", e);
                fetchOfficialDatabase();
            }
        }

        try {
            LOGGER.info("Reading unofficial database in {}", unofficialDbPath);
            // Initialize empty unofficial database if needed
            if (!Files.isRegularFile(unofficialDbPath)) {
                unofficialJsonCache = new JsonObject();
                lastModifiedCache = System.currentTimeMillis();
                persistUnofficialDatabase();
                return;
            }
            // Read json from disk
            String jsonString = Files.readString(unofficialDbPath);
            unofficialJsonCache = new JsonParser().parse(jsonString).getAsJsonObject();
            if (!isAgent) {
                // Otherwise clear unofficial database from official packs metadata
                LOGGER.debug("Cleaning unofficial database.");
                // Remove official packs from unofficial metadata database file
                for (String uuid : unofficialJsonCache.keySet()) {
                    if (isOfficialPack(uuid)) {
                        unofficialJsonCache.remove(uuid);
                        lastModifiedCache = System.currentTimeMillis();
                    }
                }
                persistUnofficialDatabase();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialize unofficial metadata database", e);
            throw new IllegalStateException("Failed to initialize unofficial metadata database");
        }
    }

    public Optional<DatabasePackMetadata> getPackMetadata(String uuid) {
        LOGGER.debug("Fetching metadata for pack: {}", uuid);
        Optional<DatabasePackMetadata> metadata = this.getOfficialMetadata(uuid);
        if (!metadata.isPresent()) {
            metadata = this.getUnofficialMetadata(uuid);
        }
        return metadata;
    }

    public boolean isOfficialPack(String uuid) {
        LOGGER.debug("Looking in official database for pack: {}", uuid);
        return cachedOfficialDatabase.containsKey(uuid);
    }

    private void refreshOfficialCache(JsonObject packsRoot) {
        // Go through all packs
        packsRoot.keySet().forEach(key -> {
            JsonObject packMetadata = packsRoot.getAsJsonObject(key);
            String uuid = packMetadata.get("uuid").getAsString();
            cachedOfficialDatabase.put(uuid, packMetadata);
        });
    }

    public Optional<DatabasePackMetadata> getOfficialMetadata(String uuid) {
        LOGGER.debug("Fetching metadata from official database for pack: {}", uuid);
        // missing
        if(!isOfficialPack(uuid)) {
            return Optional.empty();
        }
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

    public Optional<DatabasePackMetadata> getUnofficialMetadata(String uuid) {
        LOGGER.debug("Fetching metadata from unofficial database for pack: {}", uuid);
        // Fetch from unofficial metadata cache database
        if (unofficialJsonCache.has(uuid)) {
            LOGGER.debug("Unofficial metadata found for pack: {}", uuid);
            JsonObject packMetadata = unofficialJsonCache.getAsJsonObject(uuid);
            return Optional.of(new DatabasePackMetadata(
                    uuid,
                    Optional.ofNullable(packMetadata.get("title")).map(JsonElement::getAsString).orElse(null),
                    Optional.ofNullable(packMetadata.get("description")).map(JsonElement::getAsString).orElse(null),
                    Optional.ofNullable(packMetadata.get("image")).map(JsonElement::getAsString).orElse(null),
                    false
            ));
        }
        // Missing metadata
        return Optional.empty();
    }

    private void fetchOfficialDatabase() {
        try {
            LOGGER.debug("Fetching official metadata database");
            // Get a guest token
            URL tokenUrl = new URL(LUNII_GUEST_TOKEN_URL);
            HttpURLConnection tokenConnection = (HttpURLConnection) tokenUrl.openConnection();
            tokenConnection.setRequestMethod("GET");
            tokenConnection.setConnectTimeout(10000);
            tokenConnection.setReadTimeout(10000);
            int tokenStatusCode = tokenConnection.getResponseCode();
            if (tokenStatusCode != 200) {
                LOGGER.error("Failed to fetch guest token. Status code: {}", tokenStatusCode);
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
            LOGGER.debug("Guest token: {}", token);

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
                LOGGER.error("Failed to fetch official metadata database. Status code: {}", statusCode);
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
                // Update official database on RAM
                refreshOfficialCache(response);
                // Update official database on disk
                writeDatabaseFile(officialDbPath, response);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to fetch official metadata database.", e);
        }
    }

    public void refreshUnofficialCache(DatabasePackMetadata meta) {
        // Refresh unofficial database only if the pack isn't an official one
        if(isOfficialPack(meta.getUuid())) {
            return;
        }
        // Update unofficial database
        String uuid = meta.getUuid();
        LOGGER.debug("Updating unofficial metadata cache for {}", uuid);
        // Find old value
        JsonObject oldValue = null;
        if(unofficialJsonCache.has(uuid)) {
            oldValue = unofficialJsonCache.getAsJsonObject(uuid);
        }
        // New pack metadata
        JsonObject newValue = new JsonObject();
        newValue.addProperty("uuid", uuid);
        Optional.ofNullable(meta.getTitle()).ifPresent(t -> newValue.addProperty("title", t));
        Optional.ofNullable(meta.getDescription()).ifPresent(t -> newValue.addProperty("description", t));
        Optional.ofNullable(meta.getThumbnail()).ifPresent(t -> newValue.addProperty("image", t));
        // Need cache update if different
        if (!newValue.equals(oldValue)) {
            LOGGER.info("Cache updating of {}", uuid);
            lastModifiedCache = System.currentTimeMillis();
            unofficialJsonCache.add(uuid, newValue);
        }
    }

    public void persistUnofficialDatabase() throws IOException {
        // file last modified time
        long lastModifiedFile = -1l;
        if (Files.isRegularFile(unofficialDbPath)) {
            lastModifiedFile = Files.getLastModifiedTime(unofficialDbPath).toMillis();
        }
        // if cache updated, write json database to file
        if (lastModifiedCache > lastModifiedFile) {
            LOGGER.info("Persisting unofficial database to disk");
            writeDatabaseFile(unofficialDbPath, unofficialJsonCache);
        }
    }

    private synchronized void writeDatabaseFile(Path databasePath, JsonObject json) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(databasePath, gson.toJson(json));
    }

    private static Path officialDbPath() {
        return Path.of(StudioConfig.STUDIO_DB_OFFICIAL.getValue());
    }

    private static Path unofficialDbPath() {
        return Path.of(StudioConfig.STUDIO_DB_UNOFFICIAL.getValue());
    }

}
