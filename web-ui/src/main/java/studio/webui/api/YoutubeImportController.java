/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import studio.webui.service.LibraryService;
import studio.webui.service.YoutubeImportService;

import java.util.UUID;

public class YoutubeImportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeImportController.class);
    private final YoutubeImportService youtubeImportService;

    public YoutubeImportController() {
        this.youtubeImportService = new YoutubeImportService();
    }

    public static Router apiRouter(Vertx vertx, YoutubeImportService youtubeImportService, LibraryService libraryService) {
        Router router = Router.router(vertx);

        // Check if a downloader is installed
        router.get("/check").handler(ctx -> {
            boolean installed = youtubeImportService.isDownloaderInstalled();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(new JsonObject()
                            .put("installed", installed)
                            .put("message", installed ? "YouTube downloader is ready" : "YouTube downloader will be installed on first use")
                    ));
        });

        // Import from YouTube (Asynchronous)
        router.post("/import").handler(ctx -> {
            try {
                io.vertx.core.MultiMap form = ctx.request().formAttributes();
                String youtubeUrl = form.get("url");
                String requestId = form.get("requestId");
                
                if (youtubeUrl == null || youtubeUrl.isEmpty()) {
                    ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                            .end(Json.encode(new JsonObject().put("success", false).put("error", "URL is required")));
                    return;
                }

                // Return immediately with success
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", true)
                                .put("status", "started")
                                .put("requestId", requestId)
                        ));

                final String finalYoutubeUrl = youtubeUrl;
                final String finalRequestId = requestId;

                new Thread(() -> {
                    try {
                        LOGGER.info("Starting background YouTube import for URL: " + finalYoutubeUrl + (finalRequestId != null ? " [ID: " + finalRequestId + "]" : ""));
                        
                        JsonObject result = youtubeImportService.importFromYoutube(finalYoutubeUrl, (progress, message) -> {
                            JsonObject progressUpdate = new JsonObject()
                                    .put("requestId", finalRequestId)
                                    .put("progress", progress);
                            if (message != null) {
                                progressUpdate.put("message", message);
                            }
                            vertx.eventBus().publish("youtube.progress." + finalRequestId, progressUpdate);
                        });
                        
                        if (finalRequestId != null) {
                            vertx.eventBus().publish("youtube.result." + finalRequestId, result);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error during background YouTube import", e);
                        if (finalRequestId != null) {
                            vertx.eventBus().publish("youtube.result." + finalRequestId, new JsonObject().put("success", false).put("error", e.getMessage()));
                        }
                    }
                }).start();

            } catch (Exception e) {
                LOGGER.error("Error parsing import request", e);
                ctx.response().setStatusCode(500).end(Json.encode(new JsonObject().put("success", false).put("error", e.getMessage())));
            }
        });

        // Serve downloaded audio file
        router.get("/audio").handler(ctx -> {
            String filePath = ctx.request().getParam("path");
            if (filePath == null || filePath.isEmpty()) {
                ctx.response()
                        .setStatusCode(400)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Missing 'path' parameter")
                        ));
                return;
            }

            try {
                java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (java.nio.file.Files.exists(path) && !java.nio.file.Files.isDirectory(path)) {
                    ctx.response()
                            .putHeader("Content-Type", "audio/mpeg")
                            .sendFile(filePath);
                } else {
                    ctx.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(new JsonObject()
                                    .put("success", false)
                                    .put("error", "Audio file not found")
                            ));
                }
            } catch (Exception e) {
                LOGGER.error("Error serving audio file", e);
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Error serving audio file: " + e.getMessage())
                        ));
            }
        });

        // Serve downloaded thumbnail file
        router.get("/thumbnail").handler(ctx -> {
            String filePath = ctx.request().getParam("path");
            if (filePath == null || filePath.isEmpty()) {
                ctx.response()
                        .setStatusCode(400)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Missing 'path' parameter")
                        ));
                return;
            }

            try {
                java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (java.nio.file.Files.exists(path) && !java.nio.file.Files.isDirectory(path)) {
                    ctx.response()
                            .putHeader("Content-Type", "image/jpeg")
                            .sendFile(filePath);
                } else {
                    ctx.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(new JsonObject()
                                    .put("success", false)
                                    .put("error", "Thumbnail file not found")
                            ));
                }
            } catch (Exception e) {
                LOGGER.error("Error serving thumbnail file", e);
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Error serving thumbnail file: " + e.getMessage())
                        ));
            }
        });

        // Cleanup temporary files
        router.post("/cleanup").blockingHandler(ctx -> {
            JsonObject requestBody = ctx.getBodyAsJson();
            if (requestBody == null || !requestBody.containsKey("audioPath")) {
                ctx.response()
                        .setStatusCode(400)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Missing 'audioPath' parameter")
                        ));
                return;
            }

            String audioPath = requestBody.getString("audioPath");
            String thumbnailPath = requestBody.getString("thumbnailPath");

            try {
                youtubeImportService.cleanupFiles(audioPath, thumbnailPath);
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", true)
                        ));
            } catch (Exception e) {
                LOGGER.error("Error cleaning up files", e);
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Error cleaning up files: " + e.getMessage())
                        ));
            }
        });

        router.post("/save").handler(rc -> {
            JsonObject body = rc.getBodyAsJson();
            String title = body.getString("title");
            String audioPath = body.getString("audioPath");
            String thumbnailPath = body.getString("thumbnailPath");
            
            vertx.executeBlocking(promise -> {
                JsonObject result = youtubeImportService.createLibraryPack(title, audioPath, thumbnailPath, libraryService.libraryPath());
                promise.complete(result);
            }, false, result -> {
                if (result.succeeded()) {
                    rc.response().putHeader("content-type", "application/json").end(((JsonObject)result.result()).encode());
                } else {
                    rc.response().setStatusCode(500).end(result.cause().getMessage());
                }
            });
        });

        return router;
    }

    /**
     * Validate YouTube URL
     */
    private static boolean isValidYoutubeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // Basic YouTube URL pattern matching
        String pattern = "^(https?\\:\\/\\/)?(www\\.)?(youtube\\.com|youtu\\.?be)\\/.+$";
        return url.matches(pattern);
    }
}