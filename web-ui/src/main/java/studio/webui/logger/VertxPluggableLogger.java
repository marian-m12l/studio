/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.logger;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.metadata.logger.PluggableLogger;

public class VertxPluggableLogger implements PluggableLogger {

    private final Logger logger;

    public VertxPluggableLogger(String name) {
        logger = LoggerFactory.getLogger(name);
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Throwable t) {
        logger.error(message, t);
    }
}
