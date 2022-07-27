/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_DEFAULT)
public class ControlSettings {

    @JsonProperty("wheel")
    private boolean wheelEnabled;

    @JsonProperty("ok")
    private boolean okEnabled;

    @JsonProperty("home")
    private boolean homeEnabled;

    @JsonProperty("pause")
    private boolean pauseEnabled;

    @JsonProperty("autoplay")
    private boolean autoJumpEnabled;
}
