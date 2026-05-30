package com.lorevault.api.timeline;
import com.lorevault.api.graph.timeline.domain.CertaintyLevel;
import com.lorevault.api.graph.timeline.domain.CertaintyWeights;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CertaintyLevel enum completeness and values.
 * Verifies that all expected certainty levels are present and correctly spelled.
 */
class CertaintyLevelTest {

    @Test
    @Tag("unit")
    void contains_all_required_certainty_levels() {
        Set<String> actualValues = Arrays.stream(CertaintyLevel.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

        // Required certainty levels per event model specification
        Set<String> requiredValues = Set.of(
            "EXPLICIT", "STRONGLY_IMPLIED", "WEAKLY_IMPLIED", "HEURISTIC"
        );

        assertEquals(requiredValues, actualValues, 
            "Certainty level enum should contain exactly the required values");
    }

    @Test
    @Tag("unit")
    void enum_values_are_correctly_spelled() {
        // Test specific values to catch typos in enum names
        assertNotNull(CertaintyLevel.valueOf("EXPLICIT"));
        assertNotNull(CertaintyLevel.valueOf("STRONGLY_IMPLIED"));
        assertNotNull(CertaintyLevel.valueOf("WEAKLY_IMPLIED"));
        assertNotNull(CertaintyLevel.valueOf("HEURISTIC"));
    }

    @Test
    @Tag("unit")
    void has_expected_count() {
        assertEquals(4, CertaintyLevel.values().length,
            "Expected exactly 4 certainty levels");
    }

    @Test
    @Tag("unit")
    void heuristic_level_exists_for_defaults() {
        // HEURISTIC is specifically used for default consecutive scene relationships
        assertEquals("HEURISTIC", CertaintyLevel.HEURISTIC.name());
    }

    @Test
    @Tag("unit")
    void all_levels_have_weight_mappings() {
        // Verify that all enum values can be mapped to weights
        for (CertaintyLevel level : CertaintyLevel.values()) {
            assertDoesNotThrow(() -> CertaintyWeights.weightOf(level),
                "CertaintyLevel." + level.name() + " should have a weight mapping");
        }
    }
}