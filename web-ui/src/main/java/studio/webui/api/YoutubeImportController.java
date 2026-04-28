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
import studio.webui.service.YoutubeImportService;

public class YoutubeImportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeImportController.class);
    private final YoutubeImportService youtubeImportService;

    public YoutubeImportController() {
        this.youtubeImportService = new YoutubeImportService();
    }

    public static Router apiRouter(Vertx vertx, YoutubeImportService youtubeImportService) {
        Router router = Router.router(vertx);

        // Check if youtube-dl is installed
        router.get("/check").handler(ctx -> {
            boolean installed = youtubeImportService.isYoutubeDlInstalled();
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(new JsonObject()
                            .put("installed", installed)
                            .put("message", installed ? "youtube-dl is installed and ready" : "youtube-dl will be installed on first use")
                    ));
        });

        // Import from YouTube
        router.post("/import").blockingHandler(ctx -> {
            JsonObject requestBody = ctx.getBodyAsJson();
            if (requestBody == null || !requestBody.containsKey("url")) {
                ctx.response()
                        .setStatusCode(400)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Missing 'url' parameter in request body")
                        ));
                return;
            }

            String youtubeUrl = requestBody.getString("url");
            
            // Validate YouTube URL
            if (!isValidYoutubeUrl(youtubeUrl)) {
                ctx.response()
                        .setStatusCode(400)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Invalid YouTube URL")
                        ));
                return;
            }

            try {
                LOGGER.info("Starting YouTube import for URL: " + youtubeUrl);
                JsonObject result = youtubeImportService.importFromYoutube(youtubeUrl);
                
                if (result.getBoolean("success", false)) {
                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(result));
                } else {
                    String error = result.getString("error", "Unknown error");
                    LOGGER.error("YouTube import failed: " + error);
                    ctx.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(result));
                }
            } catch (Exception e) {
                LOGGER.error("Error during YouTube import", e);
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(new JsonObject()
                                .put("success", false)
                                .put("error", "Server error: " + e.getMessage())
                        ));
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