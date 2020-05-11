/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver;

public class StoryTellerException extends RuntimeException {
    public StoryTellerException(String message) {
        super(message);
    }

    public StoryTellerException(String message, Throwable cause) {
        super(message, cause);
    }

    public StoryTellerException(Throwable cause) {
        super(cause);
    }
}
