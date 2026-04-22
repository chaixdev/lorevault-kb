package com.lorevault.api.timeline;
import com.lorevault.api.content.timeline.application.TriadEdgePersistenceService;
import com.lorevault.api.content.timeline.infrastructure.TemporalEdgeWriteRepository;

import com.lorevault.api.ai.application.TriadOrchestrationService;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.infrastructure.LlmCallRecordGraphRepository;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.infrastructure.StatusRecordGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TriadEdgePersistenceServiceTest {

    @Mock
    private TemporalEdgeWriteRepository temporalEdgeWriteRepository;
    @Mock
    private IngestionJobGraphRepository ingestionJobGraphRepository;
    @Mock
    private StatusRecordGraphRepository statusRecordGraphRepository;
    @Mock
    private LlmCallRecordGraphRepository llmCallRecordGraphRepository;

    private TriadEdgePersistenceService service;

    private final UUID chapterId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID scene0Id = UUID.randomUUID();
    private final UUID scene1Id = UUID.randomUUID();
    private final UUID previousChapterSceneId = UUID.randomUUID();
    private final UUID statusRecordId = UUID.randomUUID();
    private final UUID callRecordId = UUID.randomUUID();

    private StatusRecord statusRecord;
    private LlmCallRecord callRecord;

    @BeforeEach
    void setUp() {
        service = new TriadEdgePersistenceService(
                temporalEdgeWriteRepository,
                ingestionJobGraphRepository,
                statusRecordGraphRepository,
                llmCallRecordGraphRepository
        );

        IngestionJob job = new IngestionJob();
        job.setId(jobId);

        when(ingestionJobGraphRepository.findFirstByChapterIdOrderByCreatedAtDesc(chapterId))
                .thenReturn(Optional.of(job));

        statusRecord = new StatusRecord();
        statusRecord.setId(statusRecordId);
        statusRecord.setProperties(Map.of("currentSceneIndex", "1"));

        callRecord = new LlmCallRecord();
        callRecord.setId(callRecordId);
        callRecord.setResponseBody("{\"ok\":true}");
        callRecord.setTruncated(false);

        when(statusRecordGraphRepository.findTriadStatusesForJob(jobId))
                .thenReturn(List.of(statusRecord));

        when(llmCallRecordGraphRepository.findLatestByJobStepAndStatusRecord(
                eq(jobId), eq("scene-analysis"), eq(statusRecordId)))
                .thenReturn(Optional.of(callRecord));
    }

    @Test
    void legacy_meets_input_is_written_as_temporal_before() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn(null);

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "meets")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene0Id), eq(scene1Id),
                typeCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any()
        );
        assertThat(typeCaptor.getValue()).isEqualTo("R:temporal.before");
        verify(temporalEdgeWriteRepository, never()).upsertAmbiguousRelation(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void crossChapterPreviousSceneId_is_persisted_even_when_not_in_current_chapter_index_map() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(previousChapterSceneId, scene1Id))
                .thenReturn(null);

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(previousChapterSceneId, scene1Id, null, 99, 1, null, "R:temporal.before", null)),
                Map.of(1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(previousChapterSceneId), eq(scene1Id),
                eq("R:temporal.before"),
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void legacy_met_by_input_is_canonicalized_to_before_with_flipped_endpoints() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn(null);
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id))
                .thenReturn(null);

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "met_by")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene1Id), eq(scene0Id),
                typeCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any()
        );
        assertThat(typeCaptor.getValue()).isEqualTo("R:temporal.before");
        verify(temporalEdgeWriteRepository, never()).upsertAmbiguousRelation(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void legacy_existing_meets_with_canonical_before_incoming_does_not_create_ambiguity() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn("R:temporal.meets");

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.before")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene0Id), eq(scene1Id),
                eq("R:temporal.before"),
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(temporalEdgeWriteRepository, never()).upsertAmbiguousRelation(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void genuinely_conflicting_normalised_relations_create_ambiguity_record() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn("R:temporal.before");

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.overlaps")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertAmbiguousRelation(
                eq(scene0Id), eq(scene1Id),
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(temporalEdgeWriteRepository, never()).upsertTemporalEdge(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void overlap_existing_with_contains_incoming_prefers_during_without_ambiguity() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn("R:temporal.overlaps");
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id))
                .thenReturn(null);

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.contains")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene1Id), eq(scene0Id),
                eq("R:temporal.during"),
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(temporalEdgeWriteRepository, never()).upsertAmbiguousRelation(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void overlap_existing_with_during_incoming_prefers_during_without_ambiguity() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn("R:temporal.overlaps");

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.during")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene0Id), eq(scene1Id),
                eq("R:temporal.during"),
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(temporalEdgeWriteRepository, never()).upsertAmbiguousRelation(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reverse_contains_existing_with_overlap_incoming_keeps_canonical_during_without_ambiguity() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn(null);
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id))
                .thenReturn("R:temporal.contains");

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.overlaps")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene0Id), eq(scene1Id),
                eq("R:temporal.during"),
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(temporalEdgeWriteRepository, never()).upsertAmbiguousRelation(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void canonical_during_and_contains_for_same_oriented_pair_still_create_ambiguity() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn("R:temporal.during");

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.contains")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertAmbiguousRelation(
                eq(scene0Id), eq(scene1Id),
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(temporalEdgeWriteRepository, never()).upsertTemporalEdge(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void canonicalization_flips_contains_to_reverse_during_edge() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn(null);
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id))
                .thenReturn(null);

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.contains")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene1Id), eq(scene0Id),
                eq("R:temporal.during"),
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void canonicalization_flips_after_to_reverse_before_edge() {
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene0Id, scene1Id))
                .thenReturn(null);
        when(temporalEdgeWriteRepository.findTemporalRelationBetween(scene1Id, scene0Id))
                .thenReturn(null);

        service.applyTriadAnalysesPostPersistence(
                chapterId,
                List.of(triad(0, 1, "R:temporal.after")),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        verify(temporalEdgeWriteRepository).upsertTemporalEdge(
                eq(scene1Id), eq(scene0Id),
                eq("R:temporal.before"),
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    private TriadOrchestrationService.TriadAnalysis triad(int prevIndex, int currIndex, String type) {
        return triad(scene0Id, scene1Id, null, prevIndex, currIndex, null, type, null);
    }

    private TriadOrchestrationService.TriadAnalysis triad(UUID prevId,
                                                          UUID currId,
                                                          UUID nextId,
                                                          Integer prevIndex,
                                                          Integer currIndex,
                                                          Integer nextIndex,
                                                          String prevToCurrType,
                                                          String currToNextType) {
        return new TriadOrchestrationService.TriadAnalysis(
                prevId,
                currId,
                nextId,
                prevIndex,
                currIndex,
                nextIndex,
                "marker",
                prevToCurrType,
                "Explicit",
                "test evidence",
                currToNextType,
                currToNextType != null ? "Explicit" : null,
                currToNextType != null ? "test evidence" : null,
                null
        );
    }

}
