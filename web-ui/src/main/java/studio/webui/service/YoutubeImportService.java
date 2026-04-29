/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Semaphore;

public class YoutubeImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeImportService.class);
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%");
    private static final Semaphore IMPORT_SEMAPHORE = new Semaphore(1);
    
    private static final String[] DOWNLOADER_COMMANDS = {
        "yt-dlp",
        "youtube-dl",
        "python -m yt_dlp",
        "python -m youtube_dl",
        "python3 -m yt_dlp",
        "python3 -m youtube_dl"
    };
    
    private String activeDownloaderCommand = "yt-dlp"; // Default
    private String cookieBrowser = null; // Browser to extract cookies from
    private static final int MAX_RETRIES = 3;
    
    public YoutubeImportService() {
        // Downloader will be checked/installed on first use to avoid blocking the event loop at startup
    }
    
    /**
     * Check if a downloader is installed and which one
     */
    public boolean isDownloaderInstalled() {
        for (String cmd : DOWNLOADER_COMMANDS) {
            if (checkCommand(cmd)) {
                activeDownloaderCommand = cmd;
                return true;
            }
        }
        return false;
    }

    private boolean checkCommand(String cmd) {
        try {
            List<String> fullCmd = new ArrayList<>();
            if (cmd.contains(" ")) {
                for (String part : cmd.split(" ")) {
                    fullCmd.add(part);
                }
            } else {
                fullCmd.add(cmd);
            }
            fullCmd.add("--version");

            Process process = new ProcessBuilder(fullCmd)
                    .redirectErrorStream(true)
                    .start();
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Command not found or not executable: " + cmd);
        }
        return false;
    }
    
    /**
     * Ensure a downloader is installed, install if necessary
     */
    public boolean ensureDownloaderInstalled() {
        if (isDownloaderInstalled()) {
            return true;
        }
        
        LOGGER.info("No YouTube downloader found, attempting to install yt-dlp...");
        
        String[] installCommands = {
            "pip install --upgrade yt-dlp",
            "pip3 install --upgrade yt-dlp",
            "python -m pip install --upgrade yt-dlp",
            "python3 -m pip install --upgrade yt-dlp"
        };

        for (String installCmd : installCommands) {
            try {
                List<String> fullCmd = new ArrayList<>();
                for (String part : installCmd.split(" ")) {
                    fullCmd.add(part);
                }
                
                Process process = new ProcessBuilder(fullCmd)
                        .redirectErrorStream(true)
                        .start();
                
                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    LOGGER.info("yt-dlp installed successfully via: " + installCmd);
                    if (isDownloaderInstalled()) {
                        return true;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error attempting to install yt-dlp via: " + installCmd, e);
            }
        }
        
        LOGGER.error("Failed to install any YouTube downloader. Please install yt-dlp manually: pip install yt-dlp");
        return false;
    }
    
    /**
     * Import audio and thumbnail from YouTube URL
     */
    public JsonObject importFromYoutube(String youtubeUrl) {
        return importFromYoutube(youtubeUrl, null);
    }

    public JsonObject createLibraryPack(String title, String audioPath, String thumbnailPath, String libraryPath) {
        JsonObject result = new JsonObject();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String packFileName = title.replaceAll("[\\\\/:*?\"<>|]", "_") + "_" + timestamp + ".zip";
        Path destPath = Paths.get(libraryPath, packFileName);
        
        try {
            String packUuid = UUID.randomUUID().toString();
            byte[] thumbData = null;
            if (thumbnailPath != null && Files.exists(Paths.get(thumbnailPath))) {
                thumbData = Files.readAllBytes(Paths.get(thumbnailPath));
            }
            
            byte[] audioData = null;
            if (audioPath != null && Files.exists(Paths.get(audioPath))) {
                audioData = Files.readAllBytes(Paths.get(audioPath));
            }

            try (OutputStream os = Files.newOutputStream(destPath);
                 java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(os)) {
                
                // 1. Add thumbnail.png to root for library view
                if (thumbData != null) {
                    java.util.zip.ZipEntry thumbEntry = new java.util.zip.ZipEntry("thumbnail.png");
                    zos.putNextEntry(thumbEntry);
                    zos.write(thumbData);
                    zos.closeEntry();
                }

                // 2. Build story.json
                java.util.zip.ZipEntry storyEntry = new java.util.zip.ZipEntry("story.json");
                zos.putNextEntry(storyEntry);
                
                com.google.gson.stream.JsonWriter writer = new com.google.gson.stream.JsonWriter(new OutputStreamWriter(zos, java.nio.charset.StandardCharsets.UTF_8));
                writer.setIndent("    ");
                writer.beginObject();
                writer.name("format").value("v1");
                writer.name("title").value(title);
                writer.name("description").value("Imported from YouTube");
                writer.name("version").value(1);
                writer.name("nightModeAvailable").value(false);
                
                // Assets names (SHA1)
                String thumbAssetName = null;
                if (thumbData != null) {
                    thumbAssetName = org.apache.commons.codec.digest.DigestUtils.sha1Hex(thumbData) + ".jpg";
                }
                String audioAssetName = null;
                if (audioData != null) {
                    audioAssetName = org.apache.commons.codec.digest.DigestUtils.sha1Hex(audioData) + ".mp3";
                }

                String coverUuid = UUID.randomUUID().toString();
                String storyGroupId = UUID.randomUUID().toString();
                String storyStageUuid = UUID.randomUUID().toString();
                String storyActionId = UUID.randomUUID().toString();

                writer.name("stageNodes").beginArray();
                
                // Cover Node (Simplified Stage)
                writer.beginObject();
                writer.name("uuid").value(coverUuid);
                writer.name("groupId").value(coverUuid);
                writer.name("name").value(title);
                writer.name("type").value("cover");
                writer.name("squareOne").value(true);
                writer.name("image").value(thumbAssetName);
                writer.name("audio").nullValue();
                writer.name("okTransition").beginObject()
                    .name("actionNode").value(storyActionId)
                    .name("optionIndex").value(0)
                    .endObject();
                writer.name("homeTransition").nullValue();
                writer.name("controlSettings").beginObject()
                    .name("wheel").value(true)
                    .name("ok").value(true)
                    .name("home").value(true)
                    .name("pause").value(true)
                    .name("autoplay").value(false)
                    .endObject();
                writer.endObject();
                
                // Story Node (Simplified Stage)
                writer.beginObject();
                writer.name("uuid").value(storyStageUuid);
                writer.name("groupId").value(storyGroupId);
                writer.name("name").value(title);
                writer.name("type").value("story");
                writer.name("image").value(thumbAssetName);
                writer.name("audio").value(audioAssetName);
                writer.name("okTransition").beginObject()
                    .name("actionNode").value(storyActionId)
                    .name("optionIndex").value(0)
                    .endObject();
                writer.name("homeTransition").beginObject()
                    .name("actionNode").value(storyActionId)
                    .name("optionIndex").value(0)
                    .endObject();
                writer.name("controlSettings").beginObject()
                    .name("wheel").value(true)
                    .name("ok").value(true)
                    .name("home").value(true)
                    .name("pause").value(true)
                    .name("autoplay").value(false)
                    .endObject();
                writer.endObject();
                
                writer.endArray();

                writer.name("actionNodes").beginArray();
                
                // Story Node (Simplified Action component)
                writer.beginObject();
                writer.name("id").value(storyActionId);
                writer.name("groupId").value(storyGroupId);
                writer.name("name").value(title);
                writer.name("type").value("story.storyaction");
                writer.name("options").beginArray().value(storyStageUuid).endArray();
                writer.endObject();
                
                writer.endArray();
                
                writer.endObject();
                writer.flush();
                zos.closeEntry();
                
                // 3. Add assets
                if (thumbAssetName != null) {
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("assets/" + thumbAssetName);
                    zos.putNextEntry(entry);
                    zos.write(thumbData);
                    zos.closeEntry();
                }
                if (audioAssetName != null) {
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("assets/" + audioAssetName);
                    zos.putNextEntry(entry);
                    zos.write(audioData);
                    zos.closeEntry();
                }
            }
            
            // Cleanup temp files
            if (audioPath != null) Files.deleteIfExists(Paths.get(audioPath));
            if (thumbnailPath != null) Files.deleteIfExists(Paths.get(thumbnailPath));
            
            return result.put("success", true).put("packPath", packFileName).put("uuid", packUuid);
        } catch (Exception e) {
            LOGGER.error("Failed to create library pack", e);
            return result.put("success", false).put("error", e.getMessage());
        }
    }

    public JsonObject importFromYoutube(String youtubeUrl, BiConsumer<Double, String> progressConsumer) {
        JsonObject result = new JsonObject();
        
        try {
            IMPORT_SEMAPHORE.acquire();
            try {
                // Check and install downloader if needed
                if (!isDownloaderInstalled()) {
                    LOGGER.info("YouTube downloader not found, attempting installation...");
                    if (!ensureDownloaderInstalled()) {
                        return result.put("success", false)
                                .put("error", "YouTube downloader (yt-dlp) is not installed and automatic installation failed. Please install it manually: pip install yt-dlp");
                    }
                }

                // Safety delay to avoid rate limiting
                LOGGER.info("Applying safety delay to avoid rate limiting...");
                if (progressConsumer != null) {
                    progressConsumer.accept(0.0, "Waiting 15 seconds to avoid rate limiting...");
                }
                Thread.sleep(15000);

                String tempDir = System.getProperty("java.io.tmpdir");
                String timestamp = String.valueOf(System.currentTimeMillis());
                String baseName = "youtube_" + timestamp;
                String thumbnailPath = Paths.get(tempDir, baseName + "_thumb.jpg").toString();
                
                // --- METADATA EXTRACTION WITH AUTO-RETRY ---
                LOGGER.info("Fetching video metadata for: " + youtubeUrl);
                
                JsonObject metadata = null;
                // Anonymous: Try bypass clients first
                String[] clientsToTry = new String[]{"ios", "android", "tv", "mweb", "web"};
                
                for (String client : clientsToTry) {
                    LOGGER.info("Trying metadata extraction with client: " + client);
                    metadata = getVideoMetadata(youtubeUrl, thumbnailPath, client, progressConsumer);
                    if (metadata.containsKey("title")) {
                        break;
                    }
                    LOGGER.warn("Client " + client + " failed to extract metadata. Retrying with next client in 3s...");
                    Thread.sleep(3000);
                }

                if (metadata == null || !metadata.containsKey("title")) {
                    return result.put("success", false).put("error", "YouTube blocked the request or video not found.");
                }
                
                String title = metadata.getString("title");
                String thumbnailBase64 = null;
                
                // 2. Process thumbnail
                if (!Files.exists(Paths.get(thumbnailPath))) {
                    // yt-dlp didn't write the thumbnail, try downloading it manually
                    String thumbUrl = metadata.getString("thumbnail");
                    if (thumbUrl != null) {
                        LOGGER.info("Attempting manual thumbnail download from: " + thumbUrl);
                        if (!downloadThumbnail(thumbUrl, thumbnailPath)) {
                            // Fallback to img.youtube.com if the provided URL fails
                            String videoId = extractVideoId(youtubeUrl);
                            if (videoId != null) {
                                String fallbackUrl = "https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg";
                                LOGGER.info("Attempting fallback thumbnail download from: " + fallbackUrl);
                                downloadThumbnail(fallbackUrl, thumbnailPath);
                            }
                        }
                    }
                }

                if (Files.exists(Paths.get(thumbnailPath))) {
                    String resizedThumbnailPath = Paths.get(tempDir, baseName + "_thumb_resized.jpg").toString();
                    if (resizeImage(thumbnailPath, resizedThumbnailPath, 320, 240)) {
                        Files.deleteIfExists(Paths.get(thumbnailPath));
                        thumbnailPath = resizedThumbnailPath;
                    }
                    if (Files.exists(Paths.get(thumbnailPath))) {
                        thumbnailBase64 = encodeFileToBase64(thumbnailPath);
                    }
                }
                
                // 3. Download audio
                String audioPath = Paths.get(tempDir, baseName + ".mp3").toString();
                LOGGER.info("Downloading audio for: " + title);
                
                boolean audioDownloaded = downloadAudio(youtubeUrl, audioPath, progressConsumer);
                
                if (!audioDownloaded) {
                    if (thumbnailPath != null) Files.deleteIfExists(Paths.get(thumbnailPath));
                    return result.put("success", false).put("error", "Failed to download audio from YouTube");
                }
                
                result.put("success", true)
                        .put("title", title)
                        .put("thumbnail", thumbnailBase64)
                        .put("audioPath", audioPath)
                        .put("thumbnailPath", thumbnailPath);
                
            } finally {
                IMPORT_SEMAPHORE.release();
            }
        } catch (Exception e) {
            LOGGER.error("Error importing from YouTube", e);
            result.put("success", false).put("error", "Error: " + e.getMessage());
        }
        
        return result;
    }

    private boolean downloadAudio(String youtubeUrl, String outputPath, BiConsumer<Double, String> progressConsumer) {
        try {
            List<String> command = new ArrayList<>();
            command.addAll(Arrays.asList(activeDownloaderCommand.split(" ")));
            command.add("--extract-audio");
            command.add("--audio-format");
            command.add("mp3");
            command.add("--no-check-certificates");
            
            command.add("--extractor-args");
            command.add("youtube:player-client=ios,android;player-skip=web");
            
            command.add("-o");
            command.add(outputPath);
            command.add(youtubeUrl);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            injectJsRuntimePath(pb);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[" + activeDownloaderCommand + "] " + line);
                    
                    if (progressConsumer != null) {
                        Matcher m = PROGRESS_PATTERN.matcher(line);
                        if (m.find()) {
                            try { progressConsumer.accept(Double.parseDouble(m.group(1)) / 100.0, null); } catch (Exception e) {}
                        }
                    }
                }
            }
            return process.waitFor(300, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            LOGGER.error("Error downloading audio", e);
            return false;
        }
    }

    private JsonObject getVideoMetadata(String youtubeUrl, String thumbnailOutputPath, String client, BiConsumer<Double, String> progressConsumer) {
        JsonObject metadata = new JsonObject();
        try {
            List<String> command = new ArrayList<>();
            command.addAll(Arrays.asList(activeDownloaderCommand.split(" ")));
            command.add("--quiet");
            command.add("--dump-json");
            command.add("--no-playlist");
            
            if (thumbnailOutputPath != null) {
                command.add("--write-thumbnail");
                command.add("--convert-thumbnails");
                command.add("jpg");
                command.add("-o");
                command.add("thumbnail:" + thumbnailOutputPath.replace(".jpg", ""));
            }
            
            command.add("--no-check-certificates");
            command.add("--extractor-args");
            command.add("youtube:player-client=" + client);
            
            command.add(youtubeUrl);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            injectJsRuntimePath(pb);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("{")) output.append(line);
                }
            }
            
            if (process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) {
                io.vertx.core.json.JsonObject youtubeDlJson = new io.vertx.core.json.JsonObject(output.toString());
                if (youtubeDlJson.containsKey("title")) metadata.put("title", youtubeDlJson.getString("title"));
                if (youtubeDlJson.containsKey("thumbnail")) metadata.put("thumbnail", youtubeDlJson.getString("thumbnail"));
            }
        } catch (Exception e) {
            LOGGER.error("Error getting metadata", e);
        }
        return metadata;
    }
    
    /**
     * Download thumbnail image
     */
    private boolean downloadThumbnail(String url, String outputPath) {
        try {
            URL imageUrl = new URL(url);
            java.net.URLConnection conn = imageUrl.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            
            try (InputStream in = conn.getInputStream()) {
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    ImageIO.write(image, "jpg", new File(outputPath));
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error downloading thumbnail from " + url, e);
        }
        return false;
    }
    
    /**
     * Resize image to specified dimensions
     */
    private boolean resizeImage(String inputPath, String outputPath, int width, int height) {
        try {
            BufferedImage originalImage = ImageIO.read(new File(inputPath));
            
            if (originalImage == null) {
                return false;
            }
            
            BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(originalImage, 0, 0, width, height, null);
            g.dispose();
            
            ImageIO.write(resizedImage, "jpg", new File(outputPath));
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Error resizing image", e);
            return false;
        }
    }
    
    /**
     * Encode file to base64 string
     */
    private String encodeFileToBase64(String filePath) throws IOException {
        byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
        return java.util.Base64.getEncoder().encodeToString(fileContent);
    }
    
    /**
     * Delete temporary files
     */
    public void cleanupFiles(String... filePaths) {
        for (String path : filePaths) {
            try {
                if (path != null) {
                    Files.deleteIfExists(Paths.get(path));
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to delete temporary file: " + path, e);
            }
        }
    }

    private String extractVideoId(String youtubeUrl) {
        String pattern = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/" +
                "|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/ ]{11})";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(youtubeUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Inject Node.js path into ProcessBuilder environment to provide a JS runtime for yt-dlp
     */
    private void injectJsRuntimePath(ProcessBuilder pb) {
        String nodeDir = findNodeDirectory();
        if (nodeDir != null) {
            String pathVar = "PATH";
            for (String key : pb.environment().keySet()) {
                if (key.equalsIgnoreCase("PATH")) {
                    pathVar = key;
                    break;
                }
            }
            String currentPath = pb.environment().get(pathVar);
            pb.environment().put(pathVar, nodeDir + File.pathSeparator + currentPath);
            LOGGER.debug("Injected JS runtime path: " + nodeDir);
        }
    }

    private String findNodeDirectory() {
        // Try to find node in common project locations and system paths
        String[] possibleDirs = {
            "web-ui/target/node",
            "target/node",
            "../web-ui/target/node",
            "web-ui/node",
            "node",
            "C:\\Program Files\\nodejs",
            "C:\\Program Files (x86)\\nodejs"
        };
        
        for (String dir : possibleDirs) {
            try {
                Path nodeDir = Paths.get(dir);
                if (Files.exists(nodeDir) && Files.isDirectory(nodeDir)) {
                    if (Files.exists(nodeDir.resolve("node.exe"))) {
                        String absolutePath = nodeDir.toAbsolutePath().toString();
                        LOGGER.info("Found Node.js runtime at: " + absolutePath);
                        return absolutePath;
                    }
                }
            } catch (Exception e) {
                // Ignore invalid paths
            }
        }
        
        // Final check if node is in system path
        if (checkCommand("node")) {
            return null; // Already in path, no injection needed
        }

        return null;
    }
}