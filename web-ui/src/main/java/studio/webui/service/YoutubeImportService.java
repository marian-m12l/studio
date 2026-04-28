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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class YoutubeImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeImportService.class);
    
    private static final String YOUTUBE_DL_FILENAME = "youtube-dl";
    private static final int MAX_RETRIES = 3;
    
    public YoutubeImportService() {
        // Auto-install youtube-dl on first use if not present
        ensureYoutubeDlInstalled();
    }
    
    /**
     * Check if youtube-dl is installed and install it if necessary
     */
    public boolean isYoutubeDlInstalled() {
        try {
            Process process = new ProcessBuilder(YOUTUBE_DL_FILENAME, "--version")
                    .redirectErrorStream(true)
                    .start();
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("youtube-dl not found or not executable", e);
        }
        return false;
    }
    
    /**
     * Ensure youtube-dl is installed, install if necessary
     */
    public boolean ensureYoutubeDlInstalled() {
        if (isYoutubeDlInstalled()) {
            return true;
        }
        
        LOGGER.info("youtube-dl not found, attempting to install...");
        
        try {
            // Try to install using pip
            Process pipProcess = new ProcessBuilder("pip", "install", "--upgrade", "youtube-dl")
                    .redirectErrorStream(true)
                    .start();
            
            boolean finished = pipProcess.waitFor(60, TimeUnit.SECONDS);
            if (finished && pipProcess.exitValue() == 0) {
                LOGGER.info("youtube-dl installed successfully via pip");
                return true;
            }
            
            // Try pip3
            Process pip3Process = new ProcessBuilder("pip3", "install", "--upgrade", "youtube-dl")
                    .redirectErrorStream(true)
                    .start();
            
            finished = pip3Process.waitFor(60, TimeUnit.SECONDS);
            if (finished && pip3Process.exitValue() == 0) {
                LOGGER.info("youtube-dl installed successfully via pip3");
                return true;
            }
            
            // Try python -m pip
            Process pythonPipProcess = new ProcessBuilder("python", "-m", "pip", "install", "--upgrade", "youtube-dl")
                    .redirectErrorStream(true)
                    .start();
            
            finished = pythonPipProcess.waitFor(60, TimeUnit.SECONDS);
            if (finished && pythonPipProcess.exitValue() == 0) {
                LOGGER.info("youtube-dl installed successfully via python -m pip");
                return true;
            }
            
            // Try python3 -m pip
            Process python3PipProcess = new ProcessBuilder("python3", "-m", "pip", "install", "--upgrade", "youtube-dl")
                    .redirectErrorStream(true)
                    .start();
            
            finished = python3PipProcess.waitFor(60, TimeUnit.SECONDS);
            if (finished && python3PipProcess.exitValue() == 0) {
                LOGGER.info("youtube-dl installed successfully via python3 -m pip");
                return true;
            }
            
            LOGGER.error("Failed to install youtube-dl. Please install it manually.");
            return false;
            
        } catch (Exception e) {
            LOGGER.error("Error installing youtube-dl", e);
            return false;
        }
    }
    
    /**
     * Import audio and thumbnail from YouTube URL
     */
    public JsonObject importFromYoutube(String youtubeUrl) {
        JsonObject result = new JsonObject();
        
        if (!isYoutubeDlInstalled()) {
            if (!ensureYoutubeDlInstalled()) {
                return result.put("success", false)
                        .put("error", "Failed to install youtube-dl. Please install it manually: pip install youtube-dl");
            }
        }
        
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String timestamp = String.valueOf(System.currentTimeMillis());
            String baseName = "youtube_" + timestamp;
            
            // Download audio as MP3
            String audioPath = Paths.get(tempDir, baseName + ".mp3").toString();
            boolean audioDownloaded = downloadAudio(youtubeUrl, audioPath);
            
            if (!audioDownloaded) {
                return result.put("success", false)
                        .put("error", "Failed to download audio from YouTube");
            }
            
            // Get video metadata
            JsonObject metadata = getVideoMetadata(youtubeUrl);
            if (!metadata.containsKey("title")) {
                // Clean up downloaded audio
                Files.deleteIfExists(Paths.get(audioPath));
                return result.put("success", false)
                        .put("error", "Failed to extract video metadata");
            }
            
            // Download thumbnail
            String thumbnailUrl = metadata.getString("thumbnail");
            String thumbnailPath = Paths.get(tempDir, baseName + "_thumb.jpg").toString();
            
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                boolean thumbnailDownloaded = downloadThumbnail(thumbnailUrl, thumbnailPath);
                if (thumbnailDownloaded) {
                    // Resize thumbnail to 320x240
                    String resizedThumbnailPath = Paths.get(tempDir, baseName + "_thumb_resized.jpg").toString();
                    boolean resized = resizeImage(thumbnailPath, resizedThumbnailPath, 320, 240);
                    
                    if (resized) {
                        // Delete original thumbnail
                        Files.deleteIfExists(Paths.get(thumbnailPath));
                        thumbnailPath = resizedThumbnailPath;
                    } else {
                        // If resize fails, keep original
                        LOGGER.warn("Failed to resize thumbnail, using original");
                    }
                } else {
                    thumbnailPath = null;
                }
            }
            
            // Read thumbnail as base64
            String thumbnailBase64 = null;
            if (thumbnailPath != null && Files.exists(Paths.get(thumbnailPath))) {
                thumbnailBase64 = encodeFileToBase64(thumbnailPath);
            }
            
            result.put("success", true)
                    .put("title", metadata.getString("title"))
                    .put("thumbnail", thumbnailBase64)
                    .put("audioPath", audioPath)
                    .put("thumbnailPath", thumbnailPath);
            
        } catch (Exception e) {
            LOGGER.error("Error importing from YouTube", e);
            result.put("success", false)
                    .put("error", "Error importing from YouTube: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Download audio from YouTube as MP3
     */
    private boolean downloadAudio(String youtubeUrl, String outputPath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(YOUTUBE_DL_FILENAME);
            command.add("--extract-audio");
            command.add("--audio-format");
            command.add("mp3");
            command.add("--audio-quality");
            command.add("0");
            command.add("-o");
            command.add(outputPath);
            command.add(youtubeUrl);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Read output for progress/error information
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug("youtube-dl: " + line);
                }
            }
            
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!finished) {
                process.destroyForcibly();
                LOGGER.error("youtube-dl download timed out");
                return false;
            }
            
            if (process.exitValue() != 0) {
                LOGGER.error("youtube-dl download failed with exit code: " + process.exitValue());
                return false;
            }
            
            // Check if file exists (yt-dlp might add extension automatically)
            Path mp3File = Paths.get(outputPath);
            if (!Files.exists(mp3File)) {
                // Try with .mp3 extension if not already present
                if (!outputPath.endsWith(".mp3")) {
                    mp3File = Paths.get(outputPath + ".mp3");
                }
            }
            
            return Files.exists(mp3File);
            
        } catch (Exception e) {
            LOGGER.error("Error downloading audio", e);
            return false;
        }
    }
    
    /**
     * Get video metadata from YouTube
     */
    private JsonObject getVideoMetadata(String youtubeUrl) {
        JsonObject metadata = new JsonObject();
        
        try {
            List<String> command = new ArrayList<>();
            command.add(YOUTUBE_DL_FILENAME);
            command.add("--dump-json");
            command.add("--no-playlist");
            command.add(youtubeUrl);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                String jsonOutput = output.toString();
                try {
                    io.vertx.core.json.JsonObject youtubeDlJson = new io.vertx.core.json.JsonObject(jsonOutput);
                    
                    if (youtubeDlJson.containsKey("title")) {
                        metadata.put("title", youtubeDlJson.getString("title"));
                    }
                    if (youtubeDlJson.containsKey("thumbnail")) {
                        metadata.put("thumbnail", youtubeDlJson.getString("thumbnail"));
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to parse youtube-dl JSON output", e);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error getting video metadata", e);
        }
        
        return metadata;
    }
    
    /**
     * Download thumbnail image
     */
    private boolean downloadThumbnail(String url, String outputPath) {
        try {
            URL imageUrl = new URL(url);
            BufferedImage image = ImageIO.read(imageUrl);
            
            if (image != null) {
                ImageIO.write(image, "jpg", new File(outputPath));
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Error downloading thumbnail", e);
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
}