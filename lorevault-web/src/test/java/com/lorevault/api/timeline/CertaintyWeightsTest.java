package com.lorevault.api.timeline;
import com.lorevault.api.timeline.domain.CertaintyLevel;
import com.lorevault.api.timeline.domain.CertaintyWeights;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CertaintyWeightsTest {

    @Test
    @Tag("unit")
    void weights_are_defined_and_match_specification() {
        // Verify exact weight constants per LV-082-3 specification
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

    @Test
    @Tag("unit")
    void all_certainty_levels_have_weights() {
        // Verify completeness - every enum value should have a weight
        for (CertaintyLevel level : CertaintyLevel.values()) {
            assertDoesNotThrow(() -> CertaintyWeights.weightOf(level),
                "CertaintyLevel." + level.name() + " should have a weight mapping");
            
            double weight = CertaintyWeights.weightOf(level);
            assertTrue(weight > 0.0 && weight <= 1.0,
                "Weight for " + level.name() + " should be between 0 and 1, got: " + weight);
        }
    }

    @Test
    @Tag("unit")
    void weights_are_in_descending_order_by_certainty() {
        // Higher certainty should have higher weights
        assertTrue(CertaintyWeights.weightOf(CertaintyLevel.EXPLICIT) > 
                  CertaintyWeights.weightOf(CertaintyLevel.STRONGLY_IMPLIED));
        assertTrue(CertaintyWeights.weightOf(CertaintyLevel.STRONGLY_IMPLIED) > 
                  CertaintyWeights.weightOf(CertaintyLevel.WEAKLY_IMPLIED));
        assertTrue(CertaintyWeights.weightOf(CertaintyLevel.WEAKLY_IMPLIED) > 
                  CertaintyWeights.weightOf(CertaintyLevel.HEURISTIC));
    }

    @Test
    @Tag("unit")
    void heuristic_weight_matches_default_expectation() {
        // HEURISTIC is used for default consecutive scene edges - should be 0.5
        assertEquals(0.50, CertaintyWeights.weightOf(CertaintyLevel.HEURISTIC), 1e-9);
    }

    @Test
    @Tag("unit")  
    void unknown_certainty_defaults_to_heuristic_weight() {
        // Edge case: passing null should return HEURISTIC weight (0.5)
        // This handles consecutive scene edges with no explicit certainty
        double defaultWeight = CertaintyWeights.weightOf(null);
        assertEquals(0.50, defaultWeight, 1e-9);
        
        // Should be the same as explicit HEURISTIC weight
        assertEquals(CertaintyWeights.weightOf(CertaintyLevel.HEURISTIC), defaultWeight, 1e-9);
    }

    @Test
    @Tag("unit")
    void weights_map_contains_all_enum_values() {
        var weightsMap = CertaintyWeights.weights();
        
        for (CertaintyLevel level : CertaintyLevel.values()) {
            assertTrue(weightsMap.containsKey(level),
                "Weights map should contain key for " + level.name());
        }
        
        assertEquals(CertaintyLevel.values().length, weightsMap.size(),
            "Weights map should have exactly one entry per enum value");
    }
}
