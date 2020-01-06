/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata.logger;

public interface PluggableLogger {

    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable t);
}
