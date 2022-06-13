/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata;

import lombok.Value;

@Value
public class DatabasePackMetadata {
    String uuid;
    String title;
    String description;
    String thumbnail;
    boolean official;
}
