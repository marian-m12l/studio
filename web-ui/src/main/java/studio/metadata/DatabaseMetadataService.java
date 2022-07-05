/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.core.v1.utils.stream.ThrowingConsumer;
import studio.metadata.DatabaseMetadataDTOs.DatabasePackMetadata;
import studio.metadata.DatabaseMetadataDTOs.LuniiGuestClient;
import studio.metadata.DatabaseMetadataDTOs.LuniiPacksClient;
import studio.metadata.DatabaseMetadataDTOs.PacksResponse.OfficialPack.Infos;

@ApplicationScoped
public class DatabaseMetadataService {

    private static final Logger LOGGER = LogManager.getLogger(DatabaseMetadataService.class);

    @RestClient
    LuniiGuestClient luniiGuestClient;

    @RestClient
    LuniiPacksClient luniiPacksClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "studio.db.official")
    Path dbOfficialPath;

    @ConfigProperty(name = "studio.db.unofficial")
    Path dbLibraryPath;

    // databases caches
    private Map<String, DatabasePackMetadata> dbLibraryCache;
    private Map<String, DatabasePackMetadata> dbOfficialCache;
    private long lastModifiedCache = 0;

    @PostConstruct
    public void init() {
        try {
            // Read official metadata database file
            LOGGER.info("Reading official metadata in {}", dbOfficialPath);
            if (Files.notExists(dbOfficialPath) || olderThanDays(Files.getLastModifiedTime(dbOfficialPath), 15)) {
                // (re-)create json
                fetchOfficialDatabase();
            } else {
                // Read json from disk
                dbOfficialCache = readDatabaseFile(dbOfficialPath);
            }
        } catch (IOException e) {
            // Graceful failure on invalid file content
            LOGGER.warn("Official metadata database file is invalid", e);
            fetchOfficialDatabase();
        }

        try {
            LOGGER.info("Reading library database in {}", dbLibraryPath);
            if (Files.notExists(dbLibraryPath)) {
                // Initialize empty database
                dbLibraryCache = new HashMap<>();
                lastModifiedCache = System.currentTimeMillis();
            } else {
                // Read json from disk
                dbLibraryCache = readDatabaseFile(dbLibraryPath);
            }
            // Remove official packs from library database
            LOGGER.debug("Cleaning library database.");
            if (dbLibraryCache.keySet().removeAll(dbOfficialCache.keySet())) {
                LOGGER.warn("Removing official packs found in library database!");
                lastModifiedCache = System.currentTimeMillis();
            }
            // write to disk
            persistDatabaseLibrary();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize library database", e);
        }
    }

    private static boolean olderThanDays(FileTime ft, long days) {
        return ChronoUnit.DAYS.between(ft.toInstant(), Instant.now()) > days;
    }

    public Optional<DatabasePackMetadata> getPackMetadata(String uuid) {
        LOGGER.debug("Fetching metadata for pack: {}", uuid);
        return getMetadataOfficial(uuid).or(() -> getMetadataLibrary(uuid));
    }

    private boolean isOfficialPack(String uuid) {
        LOGGER.debug("Looking in official database for pack: {}", uuid);
        return dbOfficialCache.containsKey(uuid);
    }

    public Optional<DatabasePackMetadata> getMetadataOfficial(String uuid) {
        LOGGER.debug("Fetching metadata from official database for pack: {}", uuid);
        return Optional.ofNullable(dbOfficialCache.get(uuid));
    }

    public Optional<DatabasePackMetadata> getMetadataLibrary(String uuid) {
        LOGGER.debug("Fetching metadata from library database for pack: {}", uuid);
        return Optional.ofNullable(dbLibraryCache.get(uuid));
    }

    private void fetchOfficialDatabase() {
        // default value
        dbOfficialCache = new HashMap<>();
        LOGGER.info("Fetching official database.");
        luniiGuestClient.auth() // get token
                .thenCompose(res -> luniiPacksClient.packs(res.getToken())) // get packs
                .thenApply(res -> res.getResponse().values()) // get OfficialPack list
                // convert OfficialPack to DatabasePackMetadata
                .thenAccept(ThrowingConsumer.unchecked(packs -> {
                    dbOfficialCache = packs.stream().map(op -> {
                        var locales = op.getLocalesAvailable().keySet();
                        Locale l = locales.contains(Locale.FRANCE) ? Locale.FRANCE : locales.iterator().next();
                        Infos i = op.getLocalizedInfos().get(l);
                        return new DatabasePackMetadata(op.getUuid(), i.getTitle(), i.getDescription(),
                                i.getThumbnail(), true);
                    }).filter(pm -> {
                        LOGGER.debug("Official pack: {} {}", pm.getUuid(), pm.getTitle());
                        return true;
                    }).collect(Collectors.toMap(DatabasePackMetadata::getUuid, pm -> pm, (o1, o2) -> o1));
                    // cache to disk
                    writeDatabaseFile(dbOfficialPath, dbOfficialCache);
                })) //
                .whenComplete((result, e) -> {
                    if (e != null) {
                        throw new IllegalStateException("Failed to initialize official database", e);
                    } else {
                        LOGGER.info("Fetched metadata, local database updated");
                    }
                });
    }

    public void updateDatabaseLibrary(DatabasePackMetadata meta) {
        String uuid = meta.getUuid();
        // Refresh library database only if the pack isn't an official one
        if (isOfficialPack(uuid)) {
            return;
        }
        LOGGER.debug("Updating library metadata cache for {}", uuid);
        // Find old value
        DatabasePackMetadata oldMeta = dbLibraryCache.get(uuid);
        // Need cache update if different
        if (!meta.equals(oldMeta)) {
            LOGGER.info("Cache updating of {}", uuid);
            lastModifiedCache = System.currentTimeMillis();
            dbLibraryCache.put(uuid, meta);
        }
    }

    public void persistDatabaseLibrary() throws IOException {
        // file last modified time
        long lastModifiedFile = -1l;
        if (Files.isRegularFile(dbLibraryPath)) {
            lastModifiedFile = Files.getLastModifiedTime(dbLibraryPath).toMillis();
        }
        // if cache updated, write json database to file
        if (lastModifiedCache > lastModifiedFile) {
            LOGGER.info("Persisting library database to disk");
            writeDatabaseFile(dbLibraryPath, dbLibraryCache);
        }
    }

    private Map<String, DatabasePackMetadata> readDatabaseFile(Path dbPath) throws IOException {
        return objectMapper.readValue(dbPath.toFile(), new TypeReference<Map<String, DatabasePackMetadata>>() {
        });
    }

    private void writeDatabaseFile(Path dbPath, Map<String, DatabasePackMetadata> dbCache) throws IOException {
        Files.createDirectories(dbPath.getParent());
        objectMapper.writeValue(dbPath.toFile(), dbCache);
    }
}
