/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.enriched;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EnrichedNodeType {

    STAGE((byte) 0x01, "stage"), //
    ACTION((byte) 0x02, "action"), //
    COVER((byte) 0x11, "cover"), //
    MENU_QUESTION_ACTION((byte) 0x21, "menu.questionaction"), //
    MENU_QUESTION_STAGE((byte) 0x22, "menu.questionstage"), //
    MENU_OPTIONS_ACTION((byte) 0x23, "menu.optionsaction"), //
    MENU_OPTION_STAGE((byte) 0x24, "menu.optionstage"), //
    STORY((byte) 0x31, "story"), //
    STORY_ACTION((byte) 0x32, "story.storyaction");

    private static final Map<Byte, EnrichedNodeType> BY_CODE = new HashMap<>();
    private static final Map<String, EnrichedNodeType> BY_LABEL = new HashMap<>();

    static {
        for (EnrichedNodeType e : values()) {
            BY_CODE.put(e.code, e);
            BY_LABEL.put(e.label, e);
        }
    }

    public static EnrichedNodeType fromCode(byte code) {
        return BY_CODE.get(code);
    }

    public static EnrichedNodeType fromLabel(String label) {
        return BY_LABEL.get(label);
    }

    private final byte code;
    private final String label;

    private EnrichedNodeType(byte code, String label) {
        this.code = code;
        this.label = label;
    }

    public byte getCode() {
        return code;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
