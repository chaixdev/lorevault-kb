package com.lorevault.api.timeline;

import com.lorevault.api.content.timeline.application.SceneTemporalRelationshipPersistenceService;
import com.lorevault.api.content.timeline.application.TemporalEdgeProvenance;
import com.lorevault.api.content.timeline.application.TemporalEdgeWriteRequest;
import com.lorevault.api.content.timeline.infrastructure.TemporalEdgeWriteRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class SceneTemporalRelationshipPersistenceServiceTest {

    @Mock
    private TemporalEdgeWriteRepository temporalEdgeWriteRepository;

    private SceneTemporalRelationshipPersistenceService service;

    private final UUID jobId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();
    private final UUID scene0Id = UUID.randomUUID();
    private final UUID scene1Id = UUID.randomUUID();
    private final UUID previousChapterSceneId = UUID.randomUUID();
    private final UUID statusRecordId = UUID.randomUUID();
    private final UUID callRecordId = UUID.randomUUID();

    private TemporalEdgeProvenance provenance;

    @BeforeEach
    void setUp() {
        service = new SceneTemporalRelationshipPersistenceService(temporalEdgeWriteRepository);
        provenance = new TemporalEdgeProvenance(jobId, chapterId, statusRecordId, callRecordId);
    }

    @Test
    void legacy_meets_input_is_written_as_temporal_before() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id)).thenReturn(null);

        service.applyTemporalRelationships(List.of(request(scene0Id, scene1Id, "meets")));

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene0Id), eq(scene1Id), typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any()
        );
        assertThat(typeCaptor.getValue()).isEqualTo("R:temporal.before");
    }

    @Test
    void crossChapterPreviousSceneId_is_persisted() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(previousChapterSceneId, scene1Id)).thenReturn(null);

        service.applyTemporalRelationships(List.of(request(previousChapterSceneId, scene1Id, "R:temporal.before")));

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(previousChapterSceneId), eq(scene1Id), eq("R:temporal.before"), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void legacy_met_by_input_is_canonicalized_to_before_with_flipped_endpoints() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id)).thenReturn(null);
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id)).thenReturn(null);

        service.applyTemporalRelationships(List.of(request(scene0Id, scene1Id, "met_by")));

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene1Id), eq(scene0Id), typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any()
        );
        assertThat(typeCaptor.getValue()).isEqualTo("R:temporal.before");
    }

    @Test
    void conflicting_relations_create_ambiguity_record_with_provenance() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id)).thenReturn("R:temporal.before");

        service.applyTemporalRelationships(List.of(request(scene0Id, scene1Id, "R:temporal.overlaps")));

        verify(temporalEdgeWriteRepository).upsertAmbiguousRelation(
                eq(scene0Id), eq(scene1Id), any(), any(), any(), eq(jobId.toString()), eq(chapterId.toString()), eq(statusRecordId.toString()), eq(callRecordId.toString())
        );
        verify(temporalEdgeWriteRepository, never()).upsertTemporalEdge(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void overlap_existing_with_contains_incoming_prefers_during_without_ambiguity() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id)).thenReturn("R:temporal.overlaps");
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id)).thenReturn(null);

        service.applyTemporalRelationships(List.of(request(scene0Id, scene1Id, "R:temporal.contains")));

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene1Id), eq(scene0Id), eq("R:temporal.during"), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void canonical_during_and_contains_for_same_oriented_pair_still_create_ambiguity() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id)).thenReturn("R:temporal.during");

        service.applyTemporalRelationships(List.of(request(scene0Id, scene1Id, "R:temporal.contains")));

        verify(temporalEdgeWriteRepository).upsertAmbiguousRelation(
                eq(scene0Id), eq(scene1Id), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void canonicalization_flips_after_to_reverse_before_edge() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id)).thenReturn(null);
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id)).thenReturn(null);

        service.applyTemporalRelationships(List.of(request(scene0Id, scene1Id, "R:temporal.after")));

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene1Id), eq(scene0Id), eq("R:temporal.before"), any(), any(), any(), any(), any(), any(), any()
        );
    }

    private TemporalEdgeWriteRequest request(UUID fromId, UUID toId, String type) {
        return new TemporalEdgeWriteRequest(fromId, toId, type, "Explicit", "test evidence", "marker", provenance);
    }
}
