package com.lorevault.api.infrastructure.persistence.neo4j.model;

import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.timeline.CertaintyLevel;
import com.lorevault.api.domain.timeline.TemporalRelation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TemporalEdge model mapping and field serialization.
 * Part of LV-082-3: mapping and weighting tests.
 */
@Tag("unit")
class TemporalEdgeTest {

    @Test
    void temporal_edge_has_all_required_fields() {
        var edge = new TemporalEdge();
        
        // Test field accessibility based on actual TemporalEdge structure
        assertDoesNotThrow(() -> edge.getId());
        assertDoesNotThrow(() -> edge.getTemporalRelation());
        assertDoesNotThrow(() -> edge.getCertainty());
        assertDoesNotThrow(() -> edge.getWeight());
        assertDoesNotThrow(() -> edge.getSource());
        assertDoesNotThrow(() -> edge.getRationale());
        assertDoesNotThrow(() -> edge.getEvidenceStartOffset());
        assertDoesNotThrow(() -> edge.getEvidenceEndOffset());
        assertDoesNotThrow(() -> edge.getEvidenceChunkId());
        assertDoesNotThrow(() -> edge.getLater());
    }

    @Test
    void temporal_edge_can_be_constructed_with_all_fields() {
        var sceneNode = new Scene();
        var evidenceChunkId = UUID.randomUUID();
        
        var edge = new TemporalEdge();
        edge.setId(12345L);
        edge.setTemporalRelation(TemporalRelation.BEFORE);
        edge.setCertainty(CertaintyLevel.EXPLICIT);
        edge.setWeight(0.95);
        edge.setSource("AI inference");
        edge.setRationale("Character A leaves the castle before Character B arrives");
        edge.setEvidenceStartOffset(150L);
        edge.setEvidenceEndOffset(275L);
        edge.setEvidenceChunkId(evidenceChunkId);
        edge.setLater(sceneNode);

        assertEquals(12345L, edge.getId());
        assertEquals(TemporalRelation.BEFORE, edge.getTemporalRelation());
        assertEquals(CertaintyLevel.EXPLICIT, edge.getCertainty());
        assertEquals(0.95, edge.getWeight(), 1e-9);
        assertEquals("AI inference", edge.getSource());
        assertEquals("Character A leaves the castle before Character B arrives", edge.getRationale());
        assertEquals(150L, edge.getEvidenceStartOffset());
        assertEquals(275L, edge.getEvidenceEndOffset());
        assertEquals(evidenceChunkId, edge.getEvidenceChunkId());
        assertEquals(sceneNode, edge.getLater());
    }

    @Test
    void temporal_edge_handles_null_optional_fields() {
        var edge = new TemporalEdge();
        edge.setSource(null);
        edge.setRationale(null);
        edge.setEvidenceStartOffset(null);
        edge.setEvidenceEndOffset(null);
        edge.setEvidenceChunkId(null);
        edge.setLater(null);

        assertNull(edge.getSource());
        assertNull(edge.getRationale());
        assertNull(edge.getEvidenceStartOffset());
        assertNull(edge.getEvidenceEndOffset());
        assertNull(edge.getEvidenceChunkId());
        assertNull(edge.getLater());
    }

    @Test
    void temporal_edge_handles_empty_string_fields() {
        var edge = new TemporalEdge();
        edge.setSource("");
        edge.setRationale("");

        assertEquals("", edge.getSource());
        assertEquals("", edge.getRationale());
    }

    @Test
    void temporal_edge_handles_long_text_fields() {
        var longSource = "A".repeat(1000);
        var longRationale = "B".repeat(2000);
        
        var edge = new TemporalEdge();
        edge.setSource(longSource);
        edge.setRationale(longRationale);

        assertEquals(longSource, edge.getSource());
        assertEquals(longRationale, edge.getRationale());
    }

    @Test
    void temporal_edge_handles_special_characters_in_text_fields() {
        var sourceWithSpecialChars = "AI-GPT4 → Timeline Analysis";
        var rationaleWithSpecialChars = "Scene A → Scene B: timing inferred from 'quoted text' & (parenthetical note) — em dash";
        
        var edge = new TemporalEdge();
        edge.setSource(sourceWithSpecialChars);
        edge.setRationale(rationaleWithSpecialChars);

        assertEquals(sourceWithSpecialChars, edge.getSource());
        assertEquals(rationaleWithSpecialChars, edge.getRationale());
    }

    @Test
    void temporal_edge_supports_multiline_text_fields() {
        var multilineSource = "AI Analysis:\nModel: GPT-4\nConfidence: High";
        var multilineRationale = "Line 1: Character action\nLine 2: Timeline inference\nLine 3: Conclusion";
        
        var edge = new TemporalEdge();
        edge.setSource(multilineSource);
        edge.setRationale(multilineRationale);

        assertEquals(multilineSource, edge.getSource());
        assertEquals(multilineRationale, edge.getRationale());
    }

    @Test
    void temporal_edge_accepts_all_temporal_relations() {
        var edge = new TemporalEdge();
        
        for (TemporalRelation relation : TemporalRelation.values()) {
            assertDoesNotThrow(() -> edge.setTemporalRelation(relation),
                "TemporalEdge should accept relation: " + relation.name());
            
            edge.setTemporalRelation(relation);
            assertEquals(relation, edge.getTemporalRelation());
        }
    }

    @Test
    void temporal_edge_accepts_all_certainty_levels() {
        var edge = new TemporalEdge();
        
        for (CertaintyLevel certainty : CertaintyLevel.values()) {
            assertDoesNotThrow(() -> edge.setCertainty(certainty),
                "TemporalEdge should accept certainty: " + certainty.name());
            
            edge.setCertainty(certainty);
            assertEquals(certainty, edge.getCertainty());
        }
    }

    @Test
    void temporal_edge_weight_is_numeric() {
        var edge = new TemporalEdge();
        
        // Test various weight values
        double[] testWeights = {0.0, 0.25, 0.5, 0.75, 0.95, 1.0};
        
        for (double weight : testWeights) {
            assertDoesNotThrow(() -> edge.setWeight(weight),
                "TemporalEdge should accept weight: " + weight);
            
            edge.setWeight(weight);
            assertEquals(weight, edge.getWeight(), 1e-9);
        }
    }

    @Test
    void temporal_edge_evidence_offsets_are_long_values() {
        var edge = new TemporalEdge();
        
        // Test various offset values including edge cases
        Long[] testOffsets = {0L, 1L, 100L, 1000L, Long.MAX_VALUE};
        
        for (Long offset : testOffsets) {
            assertDoesNotThrow(() -> edge.setEvidenceStartOffset(offset),
                "TemporalEdge should accept start offset: " + offset);
            assertDoesNotThrow(() -> edge.setEvidenceEndOffset(offset),
                "TemporalEdge should accept end offset: " + offset);
            
            edge.setEvidenceStartOffset(offset);
            edge.setEvidenceEndOffset(offset);
            
            assertEquals(offset, edge.getEvidenceStartOffset());
            assertEquals(offset, edge.getEvidenceEndOffset());
        }
    }

    @Test
    void temporal_edge_evidence_chunk_id_is_uuid() {
        var edge = new TemporalEdge();
        var chunkId1 = UUID.randomUUID();
        var chunkId2 = UUID.randomUUID();
        
        edge.setEvidenceChunkId(chunkId1);
        assertEquals(chunkId1, edge.getEvidenceChunkId());
        
        edge.setEvidenceChunkId(chunkId2);
        assertEquals(chunkId2, edge.getEvidenceChunkId());
        
        // Verify UUIDs are different
        assertNotEquals(chunkId1, chunkId2);
    }

    @Test
    void temporal_edge_later_node_relationship() {
        var edge = new TemporalEdge();
        var sceneNode1 = new Scene();
        var sceneNode2 = new Scene();
        
        // Initially null
        assertNull(edge.getLater());
        
        // Set first scene node
        edge.setLater(sceneNode1);
        assertEquals(sceneNode1, edge.getLater());
        
        // Change to second scene node
        edge.setLater(sceneNode2);
        assertEquals(sceneNode2, edge.getLater());
    }

    @Test
    void temporal_edge_id_is_generated() {
        var edge = new TemporalEdge();
        
        // Initially null (generated by Neo4j)
        assertNull(edge.getId());
        
        // Can be set manually (for testing)
        edge.setId(42L);
        assertEquals(42L, edge.getId());
    }
}
