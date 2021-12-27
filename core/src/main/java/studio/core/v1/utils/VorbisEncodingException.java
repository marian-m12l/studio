/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

public class VorbisEncodingException extends Exception {

    private static final long serialVersionUID = -1441290951417402064L;

    public VorbisEncodingException(String message) {
        super(message);
    }

    public VorbisEncodingException(String message, Throwable cause) {
        super(message, cause);
    }

    public VorbisEncodingException(Throwable cause) {
        super(cause);
    }
}
