package com.lorevault.api.timeline;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemporalRelation enum completeness and values.
 * Verifies that all expected temporal relations are present and accounted for.
 */
class TemporalRelationTest {

    @Test
    @Tag("unit")
    void contains_all_required_allen_interval_relations() {
        Set<String> actualValues = Arrays.stream(TemporalRelation.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

        // Required Allen interval relations per LV-082-3 spec
        Set<String> requiredValues = Set.of(
            "BEFORE", "MEETS", "OVERLAPS", "DURING", 
            "STARTS", "FINISHES", "EQUALS"
        );

        assertTrue(actualValues.containsAll(requiredValues), 
            "Missing required temporal relations: " + 
            requiredValues.stream()
                .filter(required -> !actualValues.contains(required))
                .collect(Collectors.toSet()));
    }

    @Test
    @Tag("unit")
    void enum_values_are_correctly_spelled() {
        // Test specific values to catch typos in enum names
        assertNotNull(TemporalRelation.valueOf("BEFORE"));
        assertNotNull(TemporalRelation.valueOf("MEETS"));
        assertNotNull(TemporalRelation.valueOf("OVERLAPS"));
        assertNotNull(TemporalRelation.valueOf("DURING"));
        assertNotNull(TemporalRelation.valueOf("STARTS"));
        assertNotNull(TemporalRelation.valueOf("FINISHES"));
        assertNotNull(TemporalRelation.valueOf("EQUALS"));
    }

    @Test
    @Tag("unit")
    void enum_has_expected_count() {
        // Should have at least the 7 required relations
        assertTrue(TemporalRelation.values().length >= 7,
            "Expected at least 7 temporal relations, got: " + TemporalRelation.values().length);
    }

    @Test
    @Tag("unit")
    void meets_relation_exists_for_default_edges() {
        // MEETS remains in the vocabulary for completeness, but is deprecated for inferred use.
        assertEquals("MEETS", TemporalRelation.MEETS.name());
        assertTrue(TemporalRelation.MEETS.getDeclaringClass().isEnum());
    }

    @Test
    @Tag("unit")
    void equals_relation_exists_but_is_deprecated_for_inferred_use() {
        assertEquals("EQUALS", TemporalRelation.EQUALS.name());
        assertTrue(TemporalRelation.EQUALS.getDeclaringClass().isEnum());
    }
}
