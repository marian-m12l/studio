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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.StartupEvent;
import studio.core.v1.utils.exception.StoryTellerException;
import studio.core.v1.utils.fs.FileUtils;
import studio.metadata.DatabaseMetadataDTOs.DatabasePackMetadata;
import studio.metadata.DatabaseMetadataDTOs.LuniiGuestClient;
import studio.metadata.DatabaseMetadataDTOs.LuniiPacksClient;
import studio.metadata.DatabaseMetadataDTOs.PacksResponse.OfficialPack.Infos;
import studio.metadata.DatabaseMetadataDTOs.TokenResponse;

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

    /** Delete databases on init. */
    @ConfigProperty(name = "studio.db.reset", defaultValue = "false")
    boolean dbReset;

    // databases caches
    private Map<String, DatabasePackMetadata> dbLibraryCache;
    private Map<String, DatabasePackMetadata> dbOfficialCache;
    private long lastModifiedCache = 0;

    public void init(@Observes StartupEvent ev) {
        try {
            dbLibraryCache = initDbLibrary();
            dbOfficialCache = initDbOfficial();
            writeDbs();
        } catch (IOException e) {
            throw new StoryTellerException("Failed to init databases", e);
        }
    }

    public void setDbReset(boolean dbReset) {
        this.dbReset = dbReset;
    }

    public Optional<DatabasePackMetadata> getMetadata(String uuid) {
        LOGGER.debug("Fetching metadata for pack: {}", uuid);
        return getMetadataOfficial(uuid).or(() -> getMetadataLibrary(uuid));
    }

    public Optional<DatabasePackMetadata> getMetadataOfficial(String uuid) {
        LOGGER.debug("Fetching metadata from official database for pack: {}", uuid);
        return Optional.ofNullable(dbOfficialCache.get(uuid));
    }

    public Optional<DatabasePackMetadata> getMetadataLibrary(String uuid) {
        LOGGER.debug("Fetching metadata from library database for pack: {}", uuid);
        return Optional.ofNullable(dbLibraryCache.get(uuid));
    }

    public void updateLibrary(DatabasePackMetadata meta) {
        String uuid = meta.getUuid();
        // Refresh library database only if the pack isn't an official one
        if (dbOfficialCache.containsKey(uuid)) {
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

    public void persistLibrary() throws IOException {
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

    private static boolean olderThanDays(FileTime ft, long days) {
        return ChronoUnit.DAYS.between(ft.toInstant(), Instant.now()) > days;
    }

    private Map<String, DatabasePackMetadata> initDbLibrary() throws IOException {
        if (dbReset) {
            LOGGER.info("Remove database {}", dbLibraryPath);
            Files.deleteIfExists(dbLibraryPath);
        }
        if (Files.exists(dbLibraryPath)) {
            return readDatabaseFile(dbLibraryPath);
        }
        LOGGER.info("Initialize empty library database");
        FileUtils.createDirectories("Failed to initialize library dir", dbLibraryPath.getParent());
        lastModifiedCache = System.currentTimeMillis();
        return new HashMap<>();
    }

    private Map<String, DatabasePackMetadata> initDbOfficial() throws IOException {
        if (dbReset) {
            LOGGER.info("Remove database {}", dbOfficialPath);
            Files.deleteIfExists(dbOfficialPath);
        }
        try {
            // load file younger than 15 days
            if (Files.exists(dbOfficialPath) && !olderThanDays(Files.getLastModifiedTime(dbOfficialPath), 15)) {
                return readDatabaseFile(dbOfficialPath);
            }
        } catch (IOException e) {
            // Graceful failure on invalid file content
            LOGGER.warn("Official metadata database file is invalid", e);
        }
        FileUtils.createDirectories("Failed to initialize database dir", dbOfficialPath.getParent());
        // (re-)create json
        LOGGER.info("Fetching official database.");
        // get token
        TokenResponse tr = luniiGuestClient.auth();
        // get OfficialPack list
        var packs = luniiPacksClient.packs(tr.getToken()).getResponse().values(); // get packs
        // convert OfficialPack to DatabasePackMetadata
        LOGGER.info("Parsing {} official packs.", packs.size());
        return packs.stream().map(op -> {
            var locales = op.getLocalesAvailable().keySet();
            Locale l = locales.contains(Locale.FRANCE) ? Locale.FRANCE : locales.iterator().next();
            Infos i = op.getLocalizedInfos().get(l);
            return new DatabasePackMetadata(op.getUuid(), i.getTitle(), i.getDescription(), i.getThumbnail(), true);
        }).filter(pm -> {
            LOGGER.debug("Official pack: {} {}", pm.getUuid(), pm.getTitle());
            return true;
        }).collect(Collectors.toMap(DatabasePackMetadata::getUuid, pm -> pm, (o1, o2) -> o1));
    }

    private void writeDbs() throws IOException {
        // write official db
        writeDatabaseFile(dbOfficialPath, dbOfficialCache);
        // Remove official packs from library database
        LOGGER.info("Clean official packs in library database");
        if (dbLibraryCache.keySet().removeAll(dbOfficialCache.keySet())) {
            LOGGER.warn("Removed official packs found in library database!");
            lastModifiedCache = System.currentTimeMillis();
        }
        // write library db
        persistLibrary();
    }

    private Map<String, DatabasePackMetadata> readDatabaseFile(Path dbPath) throws IOException {
        LOGGER.info("Read database {}", dbPath);
        return objectMapper.readValue(dbPath.toFile(), new TypeReference<Map<String, DatabasePackMetadata>>() {
        });
    }

    private void writeDatabaseFile(Path dbPath, Map<String, DatabasePackMetadata> dbCache) throws IOException {
        LOGGER.info("Write database {}", dbPath);
        objectMapper.writeValue(dbPath.toFile(), dbCache);
    }
}
