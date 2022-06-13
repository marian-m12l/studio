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
    @NonBlocking
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
        return CompletableFuture.supplyAsync(() -> {
            PackFormat packFormat = PackFormat.valueOf(pack.getFormat().toUpperCase());
            var newPackPath = libraryService.addConvertedPack(pack.getPath(), packFormat, pack.isAllowEnriched());
            return new SuccessPathDTO(true, newPackPath.toString());
        });
    }

    /** Remove library pack. */
    @POST
    @Path("remove")
    @NonBlocking
    public SuccessDTO remove(PathDTO pathData) {
        boolean removed = libraryService.deletePack(pathData.getPath());
        return new SuccessDTO(removed);
    }

//    public static Router apiRouter(Router router, LibraryService libraryService) {
//
//        // Local library device metadata
//        router.get("/infos").handler(ctx -> ctx.json(libraryService.infos()));
//
//        // Local library packs list
//        router.get("/packs").blockingHandler(ctx -> {
//            long t1 = System.currentTimeMillis();
//            JsonArray libraryPacks = libraryService.packs();
//            long t2 = System.currentTimeMillis();
//            LOGGER.info("Library packs scanned in {}ms", t2 - t1);
//            ctx.json(libraryPacks);
//        });
//
//        // Local library pack download
//        router.post("/download").handler(ctx -> {
//            String packPath = ctx.getBodyAsJson().getString("path");
//            LOGGER.info("Download {}", packPath);
//            ctx.response().sendFile(libraryService.getPackFile(packPath).toString());
//        });
//
//        // Local library pack upload
//        router.post("/upload").handler(BodyHandler.create() //
//                .setMergeFormAttributes(true) //
//                .setUploadsDirectory(LibraryService.tmpDirPath().toString()));
//
//        router.post("/upload").handler(ctx -> {
//            String packPath = ctx.request().getFormAttribute("path");
//            LOGGER.info("Upload {}", packPath);
//            boolean added = false;
//            Iterator<io.vertx.mutiny.ext.web.FileUpload> it = ctx.fileUploads().iterator();
//            if (it.hasNext()) {
//                added = libraryService.addPackFile(packPath, it.next().uploadedFileName());
//            }
//            if (added) {
//                ctx.json(new JsonObject().put("success", true));
//            } else {
//                LOGGER.error("Pack {} was not added to library", packPath);
//                ctx.fail(500);
//            }
//        });
//
//        // Local library pack conversion
//        router.post("/convert").handler(ctx -> {
//            JsonObject body = ctx.getBodyAsJson();
//            String packPath = body.getString("path");
//            Boolean allowEnriched = body.getBoolean("allowEnriched", false);
//            String format = body.getString("format");

    // Perform conversion/uncompression asynchronously
//            WorkerExecutor executor = vertx.createSharedWorkerExecutor("pack-converter", 1, 20, TimeUnit.MINUTES);
//            executor.executeBlocking( //
//                    future -> {
//                        try {
//                            PackFormat packFormat = PackFormat.valueOf(format.toUpperCase());
//                            Path newPackPath = libraryService.addConvertedPack(packPath, packFormat, allowEnriched);
//                            future.complete(newPackPath);
//                        } catch (IllegalArgumentException | StoryTellerException e) {
//                            future.fail(e);
//                        }
//                    }, //
//                    res -> {
//                        if (res.succeeded)()) {
//                            // Return path to converted file within library
//                            ctx.json(new JsonObject().put("success", true).put("path", res.result().toString()));
//                        } else {
//                            LOGGER.error("Failed to read or convert pack");
//                            ctx.fail(500, res.cause());
//                        }
//                    });
//        });
//
//        // Remove pack from device
//        router.post("/remove").handler(ctx -> {
//            String packPath = ctx.getBodyAsJson().getString("path");
//            boolean removed = libraryService.deletePack(packPath);
//            if (removed) {
//                ctx.json(new JsonObject().put("success", true));
//            } else {
//                LOGGER.error("Pack was not removed from library");
//                ctx.fail(500);
//            }
//        });
//
//        return router;
//    }

}
