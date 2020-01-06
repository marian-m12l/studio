/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.agent;

import net.bytebuddy.asm.Advice;

import java.util.UUID;
import java.util.logging.Logger;

public class UnofficialImageAdvice {

    @Advice.OnMethodExit
    public static void getImageUrl(@Advice.Return(readOnly = false) String retval) {
        if (retval.startsWith("https://storage.googleapis.com/lunii-data-prod/studio/")) {
            final Logger logger = Logger.getLogger("studio-agent");
            String path = "http:/" + retval.substring(retval.indexOf("/studio/"));
            logger.info("Replacing image url to trigger cache match (" + path + " -> " + UUID.nameUUIDFromBytes(path.getBytes()).toString() + ")");
            retval = path;
        }
    }

}
