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
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import io.smallrye.common.annotation.NonBlocking;
import studio.core.v1.utils.PackFormat;
import studio.webui.model.LibraryDTOs.PackDTO;
import studio.webui.model.LibraryDTOs.PathDTO;
import studio.webui.model.LibraryDTOs.SuccessDTO;
import studio.webui.model.LibraryDTOs.SuccessPathDTO;
import studio.webui.model.LibraryDTOs.UuidPacksDTO;
import studio.webui.service.LibraryService;

@Path("/api/library")
public class LibraryController {

    private static final Logger LOGGER = LogManager.getLogger(LibraryController.class);

    @Inject
    LibraryService libraryService;

    /**
     * MultipartForm with 3 params/parts :
     * <ul>
     * <li>pack (APPLICATION_OCTET_STREAM)</li>
     * <li>path (TEXT_PLAIN)</li>
     * <li>uuid (TEXT_PLAIN)</li>
     * </ul>
     */
    public static class UploadFormData {
        @FormParam("pack")
        @PartType(MediaType.APPLICATION_OCTET_STREAM)
        FileUpload pack;
    }

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
    public List<UuidPacksDTO> packs() {
        long t1 = System.currentTimeMillis();
        List<UuidPacksDTO> libraryPacks = libraryService.packs();
        long t2 = System.currentTimeMillis();
        LOGGER.info("Library packs scanned in {}ms", t2 - t1);
        return libraryPacks;
    }

    /** Download existing library pack. */
    @POST
    @Path("download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @NonBlocking
    public java.nio.file.Path downloadZip(PathDTO pathData) {
        LOGGER.info("Download pack '{}'", pathData.getPath());
        return libraryService.getPackFile(pathData.getPath());
    }

    /** Upload new library pack. */
    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @NonBlocking
    public SuccessDTO uploadZip(@MultipartForm UploadFormData formData) {
        String destName = formData.pack.fileName();
        String uploadedName = formData.pack.uploadedFile().toString();
        LOGGER.info("Upload pack '{}'", destName);
        boolean status = libraryService.addPackFile(destName, uploadedName);
        return new SuccessDTO(status);
    }

    /** Convert library pack. */
    @POST
    @Path("convert")
    public CompletionStage<SuccessPathDTO> convert(PackDTO pack) {
        // Perform conversion/uncompression asynchronously
        return CompletableFuture.supplyAsync(() -> {
            PackFormat packFormat = PackFormat.valueOf(pack.getFormat().toUpperCase());
            var newPackPath = libraryService.convertPack(pack.getPath(), packFormat, pack.isAllowEnriched());
            return new SuccessPathDTO(true, newPackPath.toString());
        });
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
