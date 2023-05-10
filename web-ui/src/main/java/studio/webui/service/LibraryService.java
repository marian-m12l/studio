/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.webui.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.core.v1.exception.StoryTellerException;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.service.PackFormat;
import studio.core.v1.service.StoryPackConverter;
import studio.core.v1.utils.io.FileUtils;
import studio.core.v1.utils.stream.ThrowingFunction;
import studio.driver.model.MetaPackDTO;
import studio.metadata.DatabaseMetadataDTOs.DatabasePackMetadata;
import studio.metadata.DatabaseMetadataService;
import studio.webui.model.LibraryDTOs.PathDTO;
import studio.webui.model.LibraryDTOs.UuidPacksDTO;

@ApplicationScoped
public class LibraryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryService.class);

    @ConfigProperty(name = "studio.library")
    Path libraryPath;

    @ConfigProperty(name = "studio.tmpdir")
    Path tmpDirPath;

    @Inject
    DatabaseMetadataService databaseMetadataService;

    private StoryPackConverter storyPackConverter;

    public void init(@Observes StartupEvent ev) {
        LOGGER.info("library path : {} (tmpdir path : {})", libraryPath, tmpDirPath);
        storyPackConverter = new StoryPackConverter(libraryPath, tmpDirPath);
        // Create the local library folder if needed
        FileUtils.createDirectories("Failed to initialize local library", libraryPath);
        // Create the temp folder if needed
        FileUtils.createDirectories("Failed to initialize temp folder", tmpDirPath);
    }

    public PathDTO infos() {
        return new PathDTO(libraryPath.toString());
    }

    public List<UuidPacksDTO> packs() {
        // List pack files in library folder
        try (Stream<Path> paths = Files.walk(libraryPath, 1)) {
            List<UuidPacksDTO> jsonMetasByUuid = paths
            // only supported packs
            .filter(p -> PackFormat.fromPath(p) != null)
            // actual read
            .map(ThrowingFunction.unchecked(this::readMetadata))
            // sort by timestamp DESC (=newer first)
            .sorted(Comparator.comparingLong(MetaPackDTO::getTimestamp).reversed())
            // group by uuid and convert to list
            .collect(Collectors.collectingAndThen(
                // group by uuid
                Collectors.groupingBy(MetaPackDTO::getUuid),
                // convert map to UuidPacksDTOs
                s -> s.entrySet().stream() //
                        .map(e -> new UuidPacksDTO(e.getKey(), e.getValue())) //
                        .collect(Collectors.toUnmodifiableList()) //
                )
            );
            // persist unofficial database cache (if needed)
            databaseMetadataService.persistLibrary();
            return jsonMetasByUuid;
        } catch (IOException e) {
            throw new StoryTellerException("Failed to read packs from local library", e);
        }
    }

    public Path getPackFile(String packPath) {
        return libraryPath.resolve(packPath);
    }

    public Path convertPack(String packName, PackFormat outFormat, boolean allowEnriched) {
        LOGGER.info("Convert pack {} to {}", packName, outFormat);
        return storyPackConverter.convert(packName, outFormat, allowEnriched);
    }

    public boolean addPackFile(String destPath, String uploadedFilePath) {
        LOGGER.info("Add pack {} from {}", destPath, uploadedFilePath);
        Path src = Path.of(uploadedFilePath);
        Path dest = libraryPath.resolve(destPath);
        try {
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new StoryTellerException("Failed to add pack to local library", e);
        }
    }

    public boolean deletePack(String packPath) {
        LOGGER.info("Delete pack '{}'", packPath);
        Path packFile = libraryPath.resolve(packPath);
        try {
            if (Files.isDirectory(packFile)) {
                FileUtils.deleteDirectory(packFile);
            } else {
                Files.deleteIfExists(packFile);
            }
            return true;
        } catch (IOException e) {
            throw new StoryTellerException("Failed to remove pack from library", e);
        }
    }

    private String base64(byte[] thumbnail) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(thumbnail);
    }

    private MetaPackDTO readMetadata(Path path) throws IOException {
        PackFormat inputFormat = PackFormat.fromPath(path);
        LOGGER.info("Read metadata {} from pack: {}", inputFormat, path.getFileName());
        StoryPackMetadata meta = inputFormat.getReader().readMetadata(path);
        if (meta == null) {
            throw new StoryTellerException("No metadata found for pack " + path);
        }
        // metadata from zip
        if(meta.getPackFormat() == PackFormat.ARCHIVE ) {
            LOGGER.debug("Refresh metadata from zip for {} ({})", meta.getUuid(), meta.getTitle());
            String thumbBase64 = Optional.ofNullable(meta.getThumbnail()).map(this::base64).orElse(null);
            databaseMetadataService.updateLibrary(new DatabasePackMetadata( //
                meta.getUuid(), meta.getTitle(), meta.getDescription(), thumbBase64, false));
        }

        MetaPackDTO mp = new MetaPackDTO();
        mp.setFormat(meta.getPackFormat().getLabel());
        mp.setUuid(meta.getUuid());
        mp.setVersion(meta.getVersion());
        mp.setPath(path.getFileName().toString());
        mp.setTimestamp(Files.getLastModifiedTime(path).toMillis());
        mp.setSectorSize((int) Math.ceil(Files.size(path)/512d));
        mp.setTitle(meta.getTitle());
        mp.setDescription(meta.getDescription());
        mp.setNightModeAvailable(meta.isNightModeAvailable());

        return databaseMetadataService.getMetadata(meta.getUuid()).map(metadata -> {
            mp.setTitle(metadata.getTitle());
            mp.setDescription(metadata.getDescription());
            mp.setImage(metadata.getThumbnail());
            mp.setOfficial(metadata.isOfficial());
            return mp;
        }).orElse(mp);
    }
}
