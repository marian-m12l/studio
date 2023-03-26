/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.webui.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.common.annotation.NonBlocking;
import studio.core.v1.service.PackFormat;
import studio.webui.model.LibraryDTOs.PackDTO;
import studio.webui.model.LibraryDTOs.PathDTO;
import studio.webui.model.LibraryDTOs.SuccessDTO;
import studio.webui.model.LibraryDTOs.SuccessPathDTO;
import studio.webui.model.LibraryDTOs.UuidPacksDTO;
import studio.webui.service.LibraryService;

@Path("/api/library")
public class LibraryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryController.class);

    @Inject
    LibraryService libraryService;

    /** Get library metadata. */
    @GET
    @Path("infos")
    @NonBlocking
    public PathDTO infos() {
        return libraryService.infos();
    }

    /** List library packs. */
    @GET
    @Path("packs")
    public CompletionStage<List<UuidPacksDTO>> packs() {
        return CompletableFuture.supplyAsync(() -> {
            long t1 = System.currentTimeMillis();
            List<UuidPacksDTO> libraryPacks = libraryService.packs();
            long t2 = System.currentTimeMillis();
            LOGGER.info("Library packs scanned in {}ms", t2 - t1);
            return libraryPacks;
        }, Infrastructure.getDefaultWorkerPool() );
    }

    /** Download existing library pack. */
    @POST
    @Path("download")
    @NonBlocking
    public java.nio.file.Path downloadZip(PathDTO pathData) {
        LOGGER.info("Download pack '{}'", pathData.getPath());
        return libraryService.getPackFile(pathData.getPath());
    }

    /** Upload new library pack. */
    @POST
    @Path("upload")
    @NonBlocking
    public SuccessDTO uploadZip(@RestForm("pack") FileUpload pack, @RestForm("path") String destName) {
        LOGGER.info("Upload pack '{}'", destName);
        String uploadedName = pack.uploadedFile().toString();
        boolean status = libraryService.addPackFile(destName, uploadedName);
        return new SuccessDTO(status);
    }

    /** Convert library pack. */
    @POST
    @Path("convert")
    public CompletionStage<SuccessPathDTO> convert(PackDTO pack) {
        return CompletableFuture.supplyAsync(() -> {
            PackFormat packFormat = PackFormat.valueOf(pack.getFormat().toUpperCase());
            long t1 = System.currentTimeMillis();
            var newPackPath = libraryService.convertPack(pack.getPath(), packFormat, pack.isAllowEnriched());
            long t2 = System.currentTimeMillis();
            LOGGER.info("Pack converted in {}ms", t2 - t1);
            return new SuccessPathDTO(true, newPackPath.toString());
        }, Infrastructure.getDefaultWorkerPool() );
    }

    /** Remove library pack. */
    @POST
    @Path("remove")
    @NonBlocking
    public SuccessDTO remove(PathDTO pathData) {
        LOGGER.info("Remove pack '{}'", pathData.getPath());
        boolean removed = libraryService.deletePack(pathData.getPath());
        return new SuccessDTO(removed);
    }
}
