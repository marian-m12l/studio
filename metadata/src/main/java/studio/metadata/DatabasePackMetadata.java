/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.metadata;

public class DatabasePackMetadata {

    private final String uuid;
    private final String title;
    private final String description;
    private final String thumbnail;
    private final boolean official;

    public DatabasePackMetadata(String uuid, String title, String description, String thumbnail, boolean official) {
        this.uuid = uuid;
        this.title = title;
        this.description = description;
        this.thumbnail = thumbnail;
        this.official = official;
    }

    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public boolean isOfficial() {
        return official;
    }

    @Override
    public String toString() {
        return "DatabasePackMetadata{" + "uuid='" + uuid + '\'' + ", title='" + title + '\'' + ", description='"
                + description + '\'' + ", thumbnail='" + thumbnail + '\'' + ", official=" + official + '}';
    }
}
