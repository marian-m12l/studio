/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.model;

import studio.core.v1.model.metadata.StoryPackMetadata;

import java.nio.file.Path;

public class LibraryPack {

    private final Path path;
    private final long timestamp;
    private final StoryPackMetadata metadata;

    public LibraryPack(Path path, long timestamp, StoryPackMetadata metadata) {
        this.path = path;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public Path getPath() {
        return path;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public StoryPackMetadata getMetadata() {
        return metadata;
    }
}
