package com.lorevault.api.domain.timeline;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CertaintyWeightsTest {

    @Test
    @Tag("unit")
    void weights_are_defined_and_match_table() {
        assertEquals(0.95, CertaintyWeights.weightOf(CertaintyLevel.EXPLICIT), 1e-9);
        assertEquals(0.80, CertaintyWeights.weightOf(CertaintyLevel.STRONGLY_IMPLIED), 1e-9);
        assertEquals(0.60, CertaintyWeights.weightOf(CertaintyLevel.WEAKLY_IMPLIED), 1e-9);
        assertEquals(0.50, CertaintyWeights.weightOf(CertaintyLevel.HEURISTIC), 1e-9);
    }

    @Test
    @Tag("unit")
    void weights_map_is_immutable() {
        assertThrows(UnsupportedOperationException.class, () ->
            CertaintyWeights.weights().put(CertaintyLevel.EXPLICIT, 1.0)
        );
    }
}
