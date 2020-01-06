/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata.logger;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaUtilPluggableLogger implements PluggableLogger {

    private final Logger logger;

    public JavaUtilPluggableLogger(String name) {
        logger = Logger.getLogger(name);
    }

    @Override
    public void debug(String message) {
        logger.log(Level.FINEST, message);
    }

    @Override
    public void info(String message) {
        logger.log(Level.INFO, message);
    }

    @Override
    public void warn(String message) {
        logger.log(Level.WARNING, message);
    }

    @Override
    public void error(String message) {
        logger.log(Level.SEVERE, message);
    }

    @Override
    public void error(String message, Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }
}
