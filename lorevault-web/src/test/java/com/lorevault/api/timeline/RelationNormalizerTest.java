package com.lorevault.api.timeline;
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

class RelationNormalizerTest {

    @Test
    @Tag("unit")
    void normalizes_before_after() {
        var n1 = RelationNormalizer.normalize(TemporalRelation.BEFORE);
        assertEquals(CanonicalRelation.BEFORE, n1.relation());
        assertFalse(n1.flipped());

        var n2 = RelationNormalizer.normalize(TemporalRelation.AFTER);
        assertEquals(CanonicalRelation.BEFORE, n2.relation());
        assertTrue(n2.flipped());
    }

    @Test
    @Tag("unit")
    void normalizes_meets_met_by() {
        var n1 = RelationNormalizer.normalize(TemporalRelation.MEETS);
        assertEquals(CanonicalRelation.BEFORE, n1.relation());
        assertFalse(n1.flipped());

        var n2 = RelationNormalizer.normalize(TemporalRelation.MET_BY);
        assertEquals(CanonicalRelation.BEFORE, n2.relation());
        assertTrue(n2.flipped());
    }

    @Test
    @Tag("unit")
    void normalizes_overlaps_overlapped_by() {
        var n1 = RelationNormalizer.normalize(TemporalRelation.OVERLAPS);
        assertEquals(CanonicalRelation.OVERLAPS, n1.relation());
        assertFalse(n1.flipped());

        var n2 = RelationNormalizer.normalize(TemporalRelation.OVERLAPPED_BY);
        assertEquals(CanonicalRelation.OVERLAPS, n2.relation());
        assertTrue(n2.flipped());
    }

    @Test
    @Tag("unit")
    void normalizes_starts_started_by() {
        var n1 = RelationNormalizer.normalize(TemporalRelation.STARTS);
        assertEquals(CanonicalRelation.STARTS, n1.relation());
        assertFalse(n1.flipped());

        var n2 = RelationNormalizer.normalize(TemporalRelation.STARTED_BY);
        assertEquals(CanonicalRelation.STARTS, n2.relation());
        assertTrue(n2.flipped());
    }

    @Test
    @Tag("unit")
    void normalizes_during_contains() {
        var n1 = RelationNormalizer.normalize(TemporalRelation.DURING);
        assertEquals(CanonicalRelation.DURING, n1.relation());
        assertFalse(n1.flipped());

        var n2 = RelationNormalizer.normalize(TemporalRelation.CONTAINS);
        assertEquals(CanonicalRelation.DURING, n2.relation());
        assertTrue(n2.flipped());
    }

    @Test
    @Tag("unit")
    void normalizes_finishes_finished_by() {
        var n1 = RelationNormalizer.normalize(TemporalRelation.FINISHES);
        assertEquals(CanonicalRelation.FINISHES, n1.relation());
        assertFalse(n1.flipped());

        var n2 = RelationNormalizer.normalize(TemporalRelation.FINISHED_BY);
        assertEquals(CanonicalRelation.FINISHES, n2.relation());
        assertTrue(n2.flipped());
    }

    @Test
    @Tag("unit")
    void normalizes_equals() {
        var n1 = RelationNormalizer.normalize(TemporalRelation.EQUALS);
        assertEquals(CanonicalRelation.OVERLAPS, n1.relation());
        assertFalse(n1.flipped());
    }
}
