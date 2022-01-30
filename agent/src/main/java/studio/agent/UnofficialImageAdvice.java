/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.agent;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bytebuddy.asm.Advice;

public class UnofficialImageAdvice {

    private static final Logger LOGGER = LogManager.getLogger("studio-agent");

    private UnofficialImageAdvice() {
        throw new IllegalArgumentException("Utility class");
    }

    @Advice.OnMethodExit
    public static void getImageUrl(@Advice.Return(readOnly = false) String retval) {
        if (retval.startsWith("https://storage.googleapis.com/lunii-data-prod/studio/")) {
            String path = "http:/" + retval.substring(retval.indexOf("/studio/"));
            retval = path;
            LOGGER.info("Replacing image url to trigger cache match ({} -> {})", retval, UUID.nameUUIDFromBytes(path.getBytes()));
        }
    }

}
