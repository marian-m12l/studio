/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.model;

public class DatabasePackMetadata {

    private String uuid;
    private String title;
    private String description;
    private String thumbnail;
    private boolean official;

    public DatabasePackMetadata() {
    }

    public DatabasePackMetadata(String uuid, String title, String description, String thumbnail, boolean official) {
        this.uuid = uuid;
        this.title = title;
        this.description = description;
        this.thumbnail = thumbnail;
        this.official = official;
    }

    @Override
    public String toString() {
        return "DatabasePackMetadata{" +
                "uuid='" + uuid + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", thumbnail='" + thumbnail + '\'' +
                ", official=" + official +
                '}';
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean isOfficial() {
        return official;
    }

    public void setOfficial(boolean official) {
        this.official = official;
    }
}
