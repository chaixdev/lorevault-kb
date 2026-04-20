package com.lorevault.api.timeline;

import com.lorevault.api.ai.TriadOrchestrationService;
import com.lorevault.api.ingestion.IngestionJob;
import com.lorevault.api.ingestion.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.LlmCallRecord;
import com.lorevault.api.ingestion.LlmCallRecordGraphRepository;
import com.lorevault.api.ingestion.StatusRecord;
import com.lorevault.api.ingestion.StatusRecordGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
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
    private final UUID statusRecordId = UUID.randomUUID();
    private final UUID callRecordId = UUID.randomUUID();

    private StatusRecord statusRecord;
    private LlmCallRecord callRecord;

    @BeforeEach
    void setUp() {
        service = instantiateWithoutConstructor(TriadEdgePersistenceService.class);
        setField(service, "temporalEdgeWriteRepository", temporalEdgeWriteRepository);
        setField(service, "ingestionJobGraphRepository", ingestionJobGraphRepository);
        setField(service, "statusRecordGraphRepository", statusRecordGraphRepository);
        setField(service, "llmCallRecordGraphRepository", llmCallRecordGraphRepository);

        IngestionJob job = new IngestionJob();
        setField(job, "id", jobId);

        when(ingestionJobGraphRepository.findFirstByChapterIdOrderByCreatedAtDesc(chapterId))
                .thenReturn(Optional.of(job));

        statusRecord = new StatusRecord();
        setField(statusRecord, "id", statusRecordId);
        setField(statusRecord, "properties", Map.of("currentSceneIndex", "1"));

        callRecord = new LlmCallRecord();
        setField(callRecord, "id", callRecordId);
        setField(callRecord, "responseBody", "{\"ok\":true}");
        setField(callRecord, "truncated", false);

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
        return new TriadOrchestrationService.TriadAnalysis(
                prevIndex,
                currIndex,
                null,
                "marker",
                type,
                "Explicit",
                "test evidence",
                null,
                null,
                null,
                null
        );
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T instantiateWithoutConstructor(Class<T> type) {
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            return type.cast(unsafe.allocateInstance(type));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
