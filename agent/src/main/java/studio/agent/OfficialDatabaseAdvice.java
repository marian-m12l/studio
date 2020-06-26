/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bytebuddy.asm.Advice;
import studio.metadata.DatabaseMetadataService;
import studio.metadata.utils.DatabaseUpdateStatusHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OfficialDatabaseAdvice {

    @Advice.OnMethodExit
    public static void getDataServerToken(@Advice.Return(readOnly = true) String retval) {
        final Logger logger = Logger.getLogger("studio-agent");

        // If token is present, refresh official database (only once per session)
        if (retval != null && DatabaseUpdateStatusHolder.lastOfficialDatabaseUpdate == 0L) {
            long now = System.currentTimeMillis();
            JsonParser parser = new JsonParser();

            // Parse and verify JWT token
            String[] jwt = retval.split("\\.");
            JsonObject payload = parser.parse(new String(Base64.getUrlDecoder().decode(jwt[1]))).getAsJsonObject();

            long issuedAt = payload.get("iat").getAsLong() * 1000;
            long expires = payload.get("exp").getAsLong() * 1000;
            String uid = payload.get("uid").getAsString();
            JsonObject claims = payload.get("claims").getAsJsonObject();

            if (issuedAt <= now && expires > now
                    && !uid.equalsIgnoreCase("Guest")
                    && !claims.get("role").getAsString().equalsIgnoreCase("GUEST")) {
                logger.info("User is logged, fetching metadata database");
                DatabaseUpdateStatusHolder.lastOfficialDatabaseUpdate = now;

                try {
                    // Call service to fetch metadata for all packs
                    URL url = new URL("https://server-data-prod.lunii.com/v2/packs");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("X-AUTH-TOKEN", retval);

                    int statusCode = connection.getResponseCode();
                    if (statusCode == 200) {
                        // OK, read response body
                        InputStream inputStream = connection.getInputStream();
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                        StringBuilder body = new StringBuilder();
                        for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
                            body.append(line).append('\n');
                        }
                        bufferedReader.close();

                        // Extract metadata database from response body
                        JsonObject json = parser.parse(body.toString()).getAsJsonObject();
                        JsonObject response = json.get("response").getAsJsonObject();

                        // Try and update official database
                        logger.info("Fetched metadata, updating local database");
                        DatabaseMetadataService databaseMetadataService = new DatabaseMetadataService(true);
                        databaseMetadataService.replaceOfficialDatabase(response);
                    } else {
                        logger.log(Level.SEVERE, "Failed to fetch metadata database. Status code: " + statusCode);
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed to fetch metadata database.", e);
                }
            }
        }
    }

}
