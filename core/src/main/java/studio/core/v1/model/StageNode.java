/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import studio.core.v1.model.asset.AudioAsset;
import studio.core.v1.model.asset.ImageAsset;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;

public class StageNode extends Node {

    private String uuid;
    private ImageAsset image;
    private AudioAsset audio;
    private Transition okTransition;
    private Transition homeTransition;
    private ControlSettings controlSettings;

    public StageNode(String uuid, ImageAsset image, AudioAsset audio, Transition okTransition, Transition homeTransition, ControlSettings controlSettings, EnrichedNodeMetadata enriched) {
        super(enriched);
        this.uuid = uuid;
        this.image = image;
        this.audio = audio;
        this.okTransition = okTransition;
        this.homeTransition = homeTransition;
        this.controlSettings = controlSettings;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public ImageAsset getImage() {
        return image;
    }

    public void setImage(ImageAsset image) {
        this.image = image;
    }

    public AudioAsset getAudio() {
        return audio;
    }

    public void setAudio(AudioAsset audio) {
        this.audio = audio;
    }

    public Transition getOkTransition() {
        return okTransition;
    }

    public void setOkTransition(Transition okTransition) {
        this.okTransition = okTransition;
    }

    public Transition getHomeTransition() {
        return homeTransition;
    }

    public void setHomeTransition(Transition homeTransition) {
        this.homeTransition = homeTransition;
    }

    public ControlSettings getControlSettings() {
        return controlSettings;
    }

    public void setControlSettings(ControlSettings controlSettings) {
        this.controlSettings = controlSettings;
    }
}
