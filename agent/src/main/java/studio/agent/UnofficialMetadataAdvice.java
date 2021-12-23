/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.bytebuddy.asm.Advice;
import studio.metadata.DatabaseMetadataService;
import studio.metadata.DatabasePackMetadata;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UnofficialMetadataAdvice {

    @Advice.OnMethodExit
    public static void getInputStream(@Advice.This HttpURLConnection that, @Advice.Return(readOnly = false) InputStream retval) {
        final Logger logger = Logger.getLogger("studio-agent");

        // Fetched URL
        String url = that.getURL().toString();

        if (url.startsWith("https://server-data-prod.lunii.com/v2/packs") && url.contains("pack_uuid=")) {
            // Get fetched pack UUID from URL parameter
            String uuid = url.substring(url.indexOf("pack_uuid=") + 10);
            try {
                // Try and get metadata from unofficial database
                DatabaseMetadataService databaseMetadataService = new DatabaseMetadataService(true);
                Optional<DatabasePackMetadata> packMetadata = databaseMetadataService.getUnofficialMetadata(uuid);
                if (packMetadata.isPresent()) {
                    logger.info("Unofficial database contains metadata for fetched pack with uuid: " + uuid);

                    DatabasePackMetadata meta = packMetadata.get();

                    // Store image into expected cached path
                    String imagePath = null;
                    if (meta.getThumbnail() != null) {
                        imagePath = "/studio/" + meta.getUuid();
                        String os = System.getProperty("os.name").toLowerCase();
                        String luniitheque = os.contains("win")
                                ? System.getenv("APPDATA") + "/Luniitheque"
                                : os.contains("mac")
                                    ? System.getProperty("user.home") + "/Library/Application Support/Luniitheque"
                                    : System.getProperty("user.home") + "/.local/share/Luniitheque";
                        String cacheFilePath = luniitheque + "/images/" + UUID.nameUUIDFromBytes(("http:/" + imagePath).getBytes()).toString();
                        logger.info("Storing unofficial metadata image into local filesystem with path: " + cacheFilePath);

                        byte[] bytes = Base64.getDecoder().decode(meta.getThumbnail().substring(meta.getThumbnail().indexOf(";base64,") + 8));
                        Files.write(Path.of(cacheFilePath), bytes);
                    }

                    // Generate JSON document with unofficial metadata
                    JsonObject value = new JsonObject();
                    value.addProperty("code", "0.0");
                    JsonObject response = new JsonObject();
                    JsonObject metadata = new JsonObject();
                    metadata.addProperty("age_max", -1);
                    metadata.addProperty("age_min", -1);
                    JsonObject authors = new JsonObject();
                    JsonObject author = new JsonObject();
                    author.addProperty("image_url", "");
                    author.addProperty("name", "Studio OSS");
                    authors.add("-StudioOSS-noauthor-", author);
                    metadata.add("authors", authors);
                    metadata.addProperty("checksum", "0123456789abcdef0123456789abcdef");
                    metadata.addProperty("creation_date", 0L);
                    JsonArray credits = new JsonArray();
                    JsonObject credit1 = new JsonObject();
                    credit1.addProperty("category_title", "");
                    JsonArray songs = new JsonArray();
                    JsonObject song1 = new JsonObject();
                    song1.addProperty("credits", "");
                    song1.addProperty("song_name", "");
                    songs.add(song1);
                    credit1.add("songs", songs);
                    credits.add(credit1);
                    metadata.add("credits", credits);
                    metadata.addProperty("duration", -1);
                    metadata.addProperty("hidden", false);
                    metadata.addProperty("is_factory", false);
                    metadata.addProperty("keywords", "");
                    JsonObject locales = new JsonObject();
                    locales.addProperty("fr_FR", true); // FIXME Handle multiple locales
                    metadata.add("locales_available", locales);
                    JsonObject localized = new JsonObject();
                    JsonObject localized_frFR = new JsonObject();
                    localized_frFR.addProperty("description", meta.getDescription());
                    JsonObject image = new JsonObject();
                    image.addProperty("image_url", imagePath);
                    localized_frFR.add("image", image);
                    JsonArray previews = new JsonArray();
                    localized_frFR.add("previews", previews);
                    localized_frFR.addProperty("subtitle", "");
                    localized_frFR.addProperty("title", meta.getTitle());
                    localized.add("fr_FR", localized_frFR);
                    metadata.add("localized_infos", localized);
                    metadata.addProperty("modification_date", 0L);
                    metadata.addProperty("sampling_rate", 0);
                    metadata.addProperty("size", 0);
                    metadata.addProperty("stats_offset", 0);
                    JsonObject stories = new JsonObject();
                    JsonObject combinations = new JsonObject();
                    combinations.addProperty("columns", 0);
                    combinations.addProperty("rows", 0);
                    combinations.addProperty("sprite", "");
                    combinations.addProperty("tile_height", 0);
                    combinations.addProperty("tile_width", 0);
                    stories.add("combinations_sprite", combinations);
                    stories.addProperty("pdf_url", "");
                    JsonObject storiesObject = new JsonObject();
                    JsonObject story1 = new JsonObject();
                    JsonArray spriteIndices = new JsonArray();
                    spriteIndices.add(0);
                    spriteIndices.add(1);
                    spriteIndices.add(2);
                    story1.add("combinations_sprite_indices", spriteIndices);
                    story1.addProperty("duration", 0);
                    story1.addProperty("title", "");
                    storiesObject.add("-StudioOSS-nostory--", story1);
                    stories.add("stories", storiesObject);
                    metadata.add("stories", stories);
                    metadata.addProperty("story_count", 1);
                    metadata.addProperty("uuid", meta.getUuid());
                    response.add(meta.getUuid(), metadata);
                    value.add("response", response);
                    String json = value.toString();

                    // Replace response body
                    retval = new ByteArrayInputStream(json.getBytes());
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error while injecting unofficial metadata", e);
            }
        }
    }

}
