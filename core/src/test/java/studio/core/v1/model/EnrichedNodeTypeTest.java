package studio.core.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import studio.core.v1.model.enriched.EnrichedNodeType;

class EnrichedNodeTypeTest {

    @Test
    void enrichedNodeType() {
        for (EnrichedNodeType e : EnrichedNodeType.values()) {
            assertEquals(e, EnrichedNodeType.fromCode(e.getCode()));
            assertEquals(e, EnrichedNodeType.fromLabel(e.getLabel()));
        }
    }
}