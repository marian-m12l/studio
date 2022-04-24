/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import studio.config.StudioConfig;

public class DatabaseMetadataService {

    private static final Logger LOGGER = LogManager.getLogger(DatabaseMetadataService.class);

    private static final String THUMBNAILS_STORAGE_ROOT = "https://storage.googleapis.com/lunii-data-prod";
    private static final String LUNII_GUEST_TOKEN_URL = "https://server-auth-prod.lunii.com/guest/create";
    private static final String LUNII_PACKS_DATABASE_URL = "https://server-data-prod.lunii.com/v2/packs";

    // databases paths
    private Path officialDbPath = officialDbPath();
    private Path unofficialDbPath = unofficialDbPath();

    // databases caches : <uuid, packMetadata>
    private final Map<String, JsonObject> cachedOfficialDatabase = new HashMap<>();
    private final JsonObject unofficialJsonCache;
    private long lastModifiedCache = 0;

    public DatabaseMetadataService() {
        try {
            // Read official metadata database file
            LOGGER.info("Reading official metadata in {}", officialDbPath);
            String jsonString = Files.readString(officialDbPath);
            JsonObject jsonRoot = JsonParser.parseString(jsonString).getAsJsonObject();
            // Support newer file which has an additional wrapper: { "code": "0.0",
            // "response": { ...
            JsonObject packsRoot = jsonRoot.has("response") ? jsonRoot.getAsJsonObject("response") : jsonRoot;
            if (packsRoot == null) {
                throw new IllegalStateException("Failed to get json root node");
            }
            // Refresh cache
            refreshOfficialCache(packsRoot);
        } catch (NoSuchFileException  e) {
            // create if missing
            fetchOfficialDatabase();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            // Graceful failure on invalid file content
            LOGGER.warn("Official metadata database file is invalid", e);
            fetchOfficialDatabase();
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
            unofficialJsonCache = JsonParser.parseString(jsonString).getAsJsonObject();
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
        } catch (IOException e) {
            LOGGER.error("Failed to initialize unofficial metadata database", e);
            throw new IllegalStateException("Failed to initialize unofficial metadata database");
        }
    }

    public Optional<DatabasePackMetadata> getPackMetadata(String uuid) {
        LOGGER.debug("Fetching metadata for pack: {}", uuid);
        return getOfficialMetadata(uuid).or(() -> getUnofficialMetadata(uuid));
    }

    public boolean isOfficialPack(String uuid) {
        LOGGER.debug("Looking in official database for pack: {}", uuid);
        return cachedOfficialDatabase.containsKey(uuid);
    }

    private void refreshOfficialCache(JsonObject packsRoot) {
        for (String key : packsRoot.keySet()) {
            JsonObject packMetadata = packsRoot.getAsJsonObject(key);
            String uuid = packMetadata.get("uuid").getAsString();
            cachedOfficialDatabase.put(uuid, packMetadata);
        }
    }

    public Optional<DatabasePackMetadata> getOfficialMetadata(String uuid) {
        LOGGER.debug("Fetching metadata from official database for pack: {}", uuid);
        // missing
        if (!isOfficialPack(uuid)) {
            return Optional.empty();
        }
        return Optional.ofNullable(cachedOfficialDatabase.get(uuid)).map(packMetadata -> {
            Set<String> localesAvailable = packMetadata.getAsJsonObject("locales_available").keySet();
            String locale = localesAvailable.contains("fr_FR") ? "fr_FR" : localesAvailable.iterator().next();
            JsonObject localizedInfos = packMetadata.getAsJsonObject("localized_infos").getAsJsonObject(locale);
            return new DatabasePackMetadata(uuid, localizedInfos.get("title").getAsString(),
                    localizedInfos.get("description").getAsString(),
                    THUMBNAILS_STORAGE_ROOT + localizedInfos.getAsJsonObject("image").get("image_url").getAsString(),
                    true);
        });
    }

    public Optional<DatabasePackMetadata> getUnofficialMetadata(String uuid) {
        LOGGER.debug("Fetching metadata from unofficial database for pack: {}", uuid);
        // Fetch from unofficial metadata cache database
        if (unofficialJsonCache.has(uuid)) {
            LOGGER.debug("Unofficial metadata found for pack: {}", uuid);
            JsonObject packMetadata = unofficialJsonCache.getAsJsonObject(uuid);
            return Optional.of(new DatabasePackMetadata(uuid,
                    Optional.ofNullable(packMetadata.get("title")).map(JsonElement::getAsString).orElse(null),
                    Optional.ofNullable(packMetadata.get("description")).map(JsonElement::getAsString).orElse(null),
                    Optional.ofNullable(packMetadata.get("image")).map(JsonElement::getAsString).orElse(null), false));
        }
        // Missing metadata
        return Optional.empty();
    }

    private void fetchOfficialDatabase() {
        try {
            JsonObject resJson;
            LOGGER.debug("Fetching official metadata database");
            // Get a guest token
            resJson = restGet(LUNII_GUEST_TOKEN_URL, null);
            String token = resJson.getAsJsonObject("response").getAsJsonObject("token").get("server").getAsString();
            LOGGER.debug("Guest token: {}", token);

            // Call service to fetch metadata for all packs
            resJson = restGet(LUNII_PACKS_DATABASE_URL, token);
            JsonObject packsJson = resJson.get("response").getAsJsonObject();
            // Try and update official database
            LOGGER.info("Fetched metadata, updating local database");

            // Update official database on RAM
            refreshOfficialCache(packsJson);
            // Update official database on disk
            writeDatabaseFile(officialDbPath, packsJson);
        } catch (IOException e) {
            LOGGER.error("Failed to fetch official metadata database.", e);
        }
    }

    public void refreshUnofficialCache(DatabasePackMetadata meta) {
        // Refresh unofficial database only if the pack isn't an official one
        if (isOfficialPack(meta.getUuid())) {
            return;
        }
        // Update unofficial database
        String uuid = meta.getUuid();
        LOGGER.debug("Updating unofficial metadata cache for {}", uuid);
        // Find old value
        JsonObject oldValue = null;
        if (unofficialJsonCache.has(uuid)) {
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

    private JsonObject restGet(String url, String token) {
        // client request
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(url)) //
                .GET().header("Accept", "application/json").timeout(Duration.ofSeconds(10));
        // add token
        if (token != null) {
            reqBuilder.header("X-AUTH-TOKEN", token);
        }
        // http call
        return httpClient.sendAsync(reqBuilder.build(), BodyHandlers.ofString()) //
                .thenApply(HttpResponse::body) //
                .thenApply(JsonParser::parseString) //
                .thenApply(JsonElement::getAsJsonObject) //
                .join();
    }

    private static Path officialDbPath() {
        return Path.of(StudioConfig.STUDIO_DB_OFFICIAL.getValue());
    }

    private static Path unofficialDbPath() {
        return Path.of(StudioConfig.STUDIO_DB_UNOFFICIAL.getValue());
    }

}
