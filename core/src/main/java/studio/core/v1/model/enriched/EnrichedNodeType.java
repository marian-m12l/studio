/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.model.enriched;

public enum EnrichedNodeType {
    STAGE((byte) 0x01, "stage"),
    ACTION((byte) 0x02, "action"),
    COVER((byte) 0x11, "cover"),
    MENU_QUESTION_ACTION((byte) 0x21, "menu.questionaction"),
    MENU_QUESTION_STAGE((byte) 0x22, "menu.questionstage"),
    MENU_OPTIONS_ACTION((byte) 0x23, "menu.optionsaction"),
    MENU_OPTION_STAGE((byte) 0x24, "menu.optionstage"),
    STORY((byte) 0x31, "story"),
    STORY_ACTION((byte) 0x32, "story.storyaction");

    public final byte code;
    public final String label;

    EnrichedNodeType(byte code, String label) {
        this.code = code;
        this.label = label;
    }

    public static EnrichedNodeType fromCode(byte code) {
        for (EnrichedNodeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }

    public static EnrichedNodeType fromLabel(String label) {
        for (EnrichedNodeType t : values()) {
            if (t.label.equals(label)) {
                return t;
            }
        }
        return null;
    }
}
