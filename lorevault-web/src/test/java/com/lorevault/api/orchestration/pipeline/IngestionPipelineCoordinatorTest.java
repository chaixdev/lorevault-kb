package com.lorevault.api.orchestration.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.orchestration.signals.StageCompletedEvent;
import com.lorevault.api.orchestration.signals.StageTriggeredEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.neo4j.core.Neo4jClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionPipelineCoordinator")
class IngestionPipelineCoordinatorTest {

    // ── Test constants ───────────────────────────────────────────────

    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CHAPTER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID BOOK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    // ── Default config values ────────────────────────────────────────

    private static final long STALE_TRIGGER_GRACE = 60L;
    private static final long STALE_RUNNING_THRESHOLD = 300L;
    private static final int MAX_STAGE_ATTEMPTS = 3;

    // ── Mocks ────────────────────────────────────────────────────────

    @Mock
    private StageGraphRepository stageRepo;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Neo4jClient neo4jClient;

    private IngestionPipelineCoordinator coordinator;

    // ── Neo4jClient fluent chain mocks ───────────────────────────────

    private Neo4jClient.UnboundRunnableSpec unboundSpec;
    private Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> ongoingBindSpec;
    private Neo4jClient.MappingSpec<UUID> mappingSpec;
    private Neo4jClient.RecordFetchSpec<UUID> recordFetchSpec;

    // ── Setup ────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        coordinator = new IngestionPipelineCoordinator(
                stageRepo, eventPublisher, neo4jClient,
                STALE_TRIGGER_GRACE, STALE_RUNNING_THRESHOLD, MAX_STAGE_ATTEMPTS);

        // Build the Neo4jClient fluent chain mocks once so we can quickly
        // arm them in tests that need query results.
        unboundSpec = mockUnboundRunnableSpec();
        ongoingBindSpec = mockOngoingBindSpec();
        mappingSpec = mockMappingSpec();
        recordFetchSpec = mockRecordFetchSpec();
    }

    @SuppressWarnings("unchecked")
    private Neo4jClient.UnboundRunnableSpec mockUnboundRunnableSpec() {
        return org.mockito.Mockito.mock(Neo4jClient.UnboundRunnableSpec.class);
    }

    @SuppressWarnings("unchecked")
    private Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> mockOngoingBindSpec() {
        return org.mockito.Mockito.mock(Neo4jClient.OngoingBindSpec.class);
    }

    @SuppressWarnings("unchecked")
    private Neo4jClient.MappingSpec<UUID> mockMappingSpec() {
        return org.mockito.Mockito.mock(Neo4jClient.MappingSpec.class);
    }

    @SuppressWarnings("unchecked")
    private Neo4jClient.RecordFetchSpec<UUID> mockRecordFetchSpec() {
        return org.mockito.Mockito.mock(Neo4jClient.RecordFetchSpec.class);
    }

    /**
     * Stubs the full {@code neo4jClient.query()} fluent chain to return a given UUID
     * from {@code .one()}. Used by tests that exercise code paths calling
     * {@code findChapterId} or {@code findBookId}.
     */
    private void stubNeo4jQuery(UUID result) {
        when(neo4jClient.query(anyString())).thenReturn(unboundSpec);
        when(unboundSpec.bind(any())).thenReturn(ongoingBindSpec);
        when(ongoingBindSpec.to(anyString())).thenReturn(unboundSpec);
        when(unboundSpec.fetchAs(UUID.class)).thenReturn(mappingSpec);
        when(mappingSpec.mappedBy(any())).thenReturn(recordFetchSpec);
        when(recordFetchSpec.one()).thenReturn(Optional.ofNullable(result));
    }

    /**
     * Stubs the Neo4jClient chain leniently (returns empty from {@code .one()}) for
     * tests that do NOT exercise code paths calling findChapterId / findBookId.
     * Prevents {@code UnnecessaryStubbingException} when using
     * {@code @ExtendWith(MockitoExtension.class)}.
     */
    private void stubNeo4jQueryLenient() {
        lenient().when(neo4jClient.query(anyString())).thenReturn(unboundSpec);
        lenient().when(unboundSpec.bind(any())).thenReturn(ongoingBindSpec);
        lenient().when(ongoingBindSpec.to(anyString())).thenReturn(unboundSpec);
        lenient().when(unboundSpec.fetchAs(UUID.class)).thenReturn(mappingSpec);
        lenient().when(mappingSpec.mappedBy(any())).thenReturn(recordFetchSpec);
        lenient().when(recordFetchSpec.one()).thenReturn(Optional.empty());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static StageResult successResult(StageKey stage) {
        return StageResult.success(stage, "Completed OK", 42L);
    }

    private static StageResult failureResult(StageKey stage) {
        return StageResult.failure(stage, "Something went wrong", 42L);
    }

    private static StageResult retryableFailureResult(StageKey stage) {
        return StageResult.retryableFailure(stage, "Transient error", 42L);
    }

    private static StageCompletedEvent completedEvent(StageKey stage, UUID bookId, StageResult result) {
        return new StageCompletedEvent("test", JOB_ID, CHAPTER_ID, bookId, stage, result);
    }

    private static Stage stageWithStatus(StageKey key, StageStatus status) {
        return Stage.builder()
                .id(UUID.randomUUID())
                .jobId(JOB_ID)
                .step(key)
                .status(status)
                .attemptCount(status == StageStatus.FAILED ? MAX_STAGE_ATTEMPTS : 1)
                .triggeredAt(LocalDateTime.now().minusMinutes(10))
                .startedAt(status == StageStatus.RUNNING || status == StageStatus.COMPLETED
                        ? LocalDateTime.now().minusMinutes(4) : null)
                .completedAt(status == StageStatus.COMPLETED ? LocalDateTime.now() : null)
                .errorMessage(status == StageStatus.FAILED ? "Error" : null)
                .errorRetryable(status == StageStatus.FAILED ? false : null)
                .build();
    }

    // ====================================================================
    //  onStageCompleted — success path
    // ====================================================================

    @Nested
    @DisplayName("onStageCompleted — success")
    class OnStageCompletedSuccess {

        @Test
        void createsChapterOutputAndTriggersDownstream() {
            // CHAPTER_INDIVIDUAL_CONSOLIDATION → BOOK_INDIVIDUAL_CONSOLIDATION (book-level child)
            // Since bookId is null and child is book-level, resolveBookId calls findBookId.
            stubNeo4jQuery(BOOK_ID);

            StageKey stage = StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION;
            StageCompletedEvent event = completedEvent(stage, null, successResult(stage));

            when(stageRepo.tryTrigger(JOB_ID, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION)).thenReturn(true);

            coordinator.onStageCompleted(event);

            // Stage marked completed
            verify(stageRepo).setCompleted(JOB_ID, stage);


            // Downstream child triggered with resolved bookId
            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            StageTriggeredEvent published = eventCaptor.getValue();
            assertThat(published.getJobId()).isEqualTo(JOB_ID);
            assertThat(published.getChapterId()).isEqualTo(CHAPTER_ID);
            assertThat(published.getBookId()).isEqualTo(BOOK_ID);
            assertThat(published.getStage()).isEqualTo(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION);
        }

        @Test
        @DisplayName("triggers all children when barriers are satisfied")
        void triggersAllChildren() {
            // SCENE_SEGMENTATION → 7 chapter-level children; resolveBookId returns null directly
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.SCENE_SEGMENTATION;
            StageCompletedEvent event = completedEvent(stage, null, successResult(stage));

            // All 7 children have their barriers satisfied
            for (StageKey child : coordinator.dag().childrenOf(stage)) {
                when(stageRepo.tryTrigger(JOB_ID, child)).thenReturn(true);
            }

            coordinator.onStageCompleted(event);

            verify(stageRepo).setCompleted(JOB_ID, stage);


            // 7 events published — one per child
            verify(eventPublisher, times(7)).publishEvent(any(StageTriggeredEvent.class));

            // Collect captured events via mockingDetails
            List<StageTriggeredEvent> events = org.mockito.Mockito.mockingDetails(eventPublisher)
                    .getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("publishEvent"))
                    .map(i -> (StageTriggeredEvent) i.getArgument(0))
                    .toList();

            assertThat(events).hasSize(7);
            assertThat(events).allSatisfy(e -> {
                assertThat(e).isInstanceOf(StageTriggeredEvent.class);
                StageTriggeredEvent ste = (StageTriggeredEvent) e;
                assertThat(ste.getJobId()).isEqualTo(JOB_ID);
                assertThat(ste.getChapterId()).isEqualTo(CHAPTER_ID);
                assertThat(ste.getBookId()).isNull(); // all chapter-level children
            });

            Set<StageKey> childKeys = Set.of(
                    StageKey.CHUNKING,
                    StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION,
                    StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                    StageKey.CHAPTER_LOCATION_CONSOLIDATION,
                    StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                    StageKey.CHAPTER_EVENT_CONSOLIDATION,
                    StageKey.CHAPTER_CONCEPT_CONSOLIDATION);
            assertThat(events)
                    .extracting(e -> ((StageTriggeredEvent) e).getStage())
                    .containsExactlyInAnyOrderElementsOf(childKeys);
        }

        @Test
        void createsBookLevelOutput() {
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.BOOK_INDIVIDUAL_CONSOLIDATION;
            StageCompletedEvent event = completedEvent(stage, BOOK_ID, successResult(stage));

            coordinator.onStageCompleted(event);

            verify(stageRepo).setCompleted(JOB_ID, stage);

        }

        @Test
        @DisplayName("only triggers children for which tryTrigger returns true")
        void onlyTriggersWhenTryTriggerReturnsTrue() {
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.SCENE_SEGMENTATION;
            StageCompletedEvent event = completedEvent(stage, null, successResult(stage));

            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHUNKING)).thenReturn(true);
            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION)).thenReturn(true);
            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION)).thenReturn(false);
            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHAPTER_LOCATION_CONSOLIDATION)).thenReturn(false);
            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHAPTER_OBJECT_CONSOLIDATION)).thenReturn(false);
            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHAPTER_EVENT_CONSOLIDATION)).thenReturn(false);
            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHAPTER_CONCEPT_CONSOLIDATION)).thenReturn(false);

            coordinator.onStageCompleted(event);

            verify(eventPublisher, times(2)).publishEvent(any(StageTriggeredEvent.class));

            List<StageTriggeredEvent> events = org.mockito.Mockito.mockingDetails(eventPublisher)
                    .getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("publishEvent"))
                    .map(i -> (StageTriggeredEvent) i.getArgument(0))
                    .toList();
            assertThat(events)
                    .extracting(StageTriggeredEvent::getStage)
                    .containsExactlyInAnyOrder(StageKey.CHUNKING, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);
        }

        @Test
        @DisplayName("uses provided bookId for book-level child, skipping Neo4jClient lookup")
        void usesProvidedBookIdForBookLevelChild() {
            // CHAPTER_INDIVIDUAL_CONSOLIDATION → BOOK_INDIVIDUAL_CONSOLIDATION (book-level)
            // Event has non-null bookId → resolveBookId returns it immediately,
            // so Neo4jClient.findBookId is never called.
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION;
            StageCompletedEvent event = completedEvent(stage, BOOK_ID, successResult(stage));

            when(stageRepo.tryTrigger(JOB_ID, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION)).thenReturn(true);

            coordinator.onStageCompleted(event);

            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getBookId()).isEqualTo(BOOK_ID);

            verify(neo4jClient, never()).query(anyString());
        }

        @Test
        @DisplayName("passes null bookId for chapter-level children")
        void passesNullBookIdForChapterLevelChildren() {
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.SCENE_SEGMENTATION;
            StageCompletedEvent event = completedEvent(stage, null, successResult(stage));

            when(stageRepo.tryTrigger(any(), any())).thenReturn(true);

            coordinator.onStageCompleted(event);

            verify(eventPublisher, times(7)).publishEvent(any(StageTriggeredEvent.class));

            List<StageTriggeredEvent> events = org.mockito.Mockito.mockingDetails(eventPublisher)
                    .getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("publishEvent"))
                    .map(i -> (StageTriggeredEvent) i.getArgument(0))
                    .toList();
            // All children are chapter-level → bookId should be null in all events
            assertThat(events)
                    .extracting(StageTriggeredEvent::getBookId)
                    .allMatch(b -> b == null);
        }
    }

    // ====================================================================
    //  onStageCompleted — failure path
    // ====================================================================

    @Nested
    @DisplayName("onStageCompleted — failure")
    class OnStageCompletedFailure {

        @Test
        void marksFailedAndSkipsDownstream() {
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.SCENE_SEGMENTATION;
            StageResult result = failureResult(stage);
            StageCompletedEvent event = completedEvent(stage, null, result);

            coordinator.onStageCompleted(event);

            verify(stageRepo).setFailed(JOB_ID, stage, "Something went wrong", false);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("marks stage as failed with retryable flag")
        void marksFailedWithRetryable() {
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.CHUNKING;
            StageResult result = retryableFailureResult(stage);
            StageCompletedEvent event = completedEvent(stage, null, result);

            coordinator.onStageCompleted(event);

            verify(stageRepo).setFailed(JOB_ID, stage, "Transient error", true);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("does not call evaluateDownstream when stage fails")
        void doesNotEvaluateDownstreamOnFailure() {
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION;
            StageCompletedEvent event = completedEvent(stage, null, failureResult(stage));

            coordinator.onStageCompleted(event);

            verify(stageRepo, never()).tryTrigger(any(), any());
        }
    }

    // ====================================================================
    //  evaluateDownstream — bookId resolution
    // ====================================================================

    @Nested
    @DisplayName("evaluateDownstream — bookId resolution")
    class EvaluateDownstream {

        @Test
        @DisplayName("resolves bookId via findBookId for book-level child when bookId is null")
        void resolvesBookIdForBookLevelChild() {
            // CHAPTER_INDIVIDUAL_CONSOLIDATION → BOOK_INDIVIDUAL_CONSOLIDATION (book-level)
            // bookId is null → resolveBookId calls findBookId(chapterId)
            stubNeo4jQuery(BOOK_ID);

            StageKey stage = StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION;
            StageCompletedEvent event = completedEvent(stage, null, successResult(stage));

            when(stageRepo.tryTrigger(JOB_ID, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION)).thenReturn(true);

            coordinator.onStageCompleted(event);

            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getBookId()).isEqualTo(BOOK_ID);
        }

        @Test
        @DisplayName("passes null bookId for chapter-level child without calling findBookId")
        void chapterLevelChildNoBookIdLookup() {
            // CHAPTER_EVENT_CONSOLIDATION → CHAPTER_EVENT_EMBEDDING (chapter-level)
            // resolveBookId returns null immediately since child is chapter-level
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.CHAPTER_EVENT_CONSOLIDATION;
            StageCompletedEvent event = completedEvent(stage, null, successResult(stage));

            when(stageRepo.tryTrigger(JOB_ID, StageKey.CHAPTER_EVENT_EMBEDDING)).thenReturn(true);

            coordinator.onStageCompleted(event);

            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getBookId()).isNull();

            // findBookId should not be called since the child is chapter-level
            // (only 0 or 1 query calls — verify no calls to neo4jClient since
            //  resolveBookId returns null for chapter-level children)
            verify(neo4jClient, never()).query(anyString());
        }
    }

    // ====================================================================
    //  bootstrapJob
    // ====================================================================

    @Nested
    @DisplayName("bootstrapJob")
    class BootstrapJob {

        @Test
        @DisplayName("creates all stages and triggers root stage")
        void createsAllStagesAndTriggersRoot() {
            stubNeo4jQueryLenient();

            Map<StageKey, UUID> stageIds = Map.of(StageKey.SCENE_SEGMENTATION, UUID.randomUUID());
            when(stageRepo.createAllForJob(JOB_ID, coordinator.dag())).thenReturn(stageIds);
            when(stageRepo.tryTrigger(JOB_ID, StageKey.SCENE_SEGMENTATION)).thenReturn(true);

            coordinator.bootstrapJob(JOB_ID, CHAPTER_ID);

            verify(stageRepo).createAllForJob(JOB_ID, coordinator.dag());
            verify(stageRepo).tryTrigger(JOB_ID, StageKey.SCENE_SEGMENTATION);

            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            StageTriggeredEvent published = eventCaptor.getValue();
            assertThat(published.getJobId()).isEqualTo(JOB_ID);
            assertThat(published.getChapterId()).isEqualTo(CHAPTER_ID);
            assertThat(published.getBookId()).isNull();
            assertThat(published.getStage()).isEqualTo(StageKey.SCENE_SEGMENTATION);
        }

        @Test
        @DisplayName("skips trigger when tryTrigger returns false")
        void skipsTriggerWhenNotReady() {
            stubNeo4jQueryLenient();

            when(stageRepo.createAllForJob(JOB_ID, coordinator.dag())).thenReturn(Collections.emptyMap());
            when(stageRepo.tryTrigger(JOB_ID, StageKey.SCENE_SEGMENTATION)).thenReturn(false);

            coordinator.bootstrapJob(JOB_ID, CHAPTER_ID);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("validates DAG connectivity")
        void validatesDagConnectivity() {
            stubNeo4jQueryLenient();

            when(stageRepo.createAllForJob(any(), any())).thenReturn(Collections.emptyMap());

            coordinator.bootstrapJob(JOB_ID, CHAPTER_ID);

            // The DAG is valid — validateConnectivity returns empty, no exception
            verify(stageRepo).createAllForJob(JOB_ID, coordinator.dag());
        }
    }

    // ====================================================================
    //  recoverStaleTriggers
    // ====================================================================

    @Nested
    @DisplayName("recoverStaleTriggers")
    class RecoverStaleTriggers {

        @Test
        @DisplayName("re-publishes StageTriggered for stale TRIGGERED stages")
        void republishesForStaleStages() {
            Stage stale = stageWithStatus(StageKey.CHUNKING, StageStatus.TRIGGERED);

            when(stageRepo.findStaleTriggered(Duration.ofSeconds(STALE_TRIGGER_GRACE)))
                    .thenReturn(List.of(stale));
            // findChapterId returns CHAPTER_ID; CHUNKING is chapter-level so findBookId is not called
            stubNeo4jQuery(CHAPTER_ID);

            coordinator.recoverStaleTriggers();

            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            StageTriggeredEvent published = eventCaptor.getValue();
            assertThat(published.getJobId()).isEqualTo(JOB_ID);
            assertThat(published.getChapterId()).isEqualTo(CHAPTER_ID);
            assertThat(published.getBookId()).isNull();
            assertThat(published.getStage()).isEqualTo(StageKey.CHUNKING);
        }

        @Test
        @DisplayName("resolves bookId for book-level stale stages")
        void resolvesBookIdForBookLevelStage() {
            Stage stale = stageWithStatus(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION, StageStatus.TRIGGERED);

            when(stageRepo.findStaleTriggered(Duration.ofSeconds(STALE_TRIGGER_GRACE)))
                    .thenReturn(List.of(stale));

            // Two Neo4j queries will be made:
            //   1. findChapterId(JOB_ID) → CHAPTER_ID
            //   2. findBookId(CHAPTER_ID) → BOOK_ID
            // We need two independent chain mocks since each query() call needs its own chain.
            Neo4jClient.UnboundRunnableSpec spec1 = mockUnboundRunnableSpec();
            Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> bind1 = mockOngoingBindSpec();
            Neo4jClient.MappingSpec<UUID> map1 = mockMappingSpec();
            Neo4jClient.RecordFetchSpec<UUID> rec1 = mockRecordFetchSpec();

            Neo4jClient.UnboundRunnableSpec spec2 = mockUnboundRunnableSpec();
            Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> bind2 = mockOngoingBindSpec();
            Neo4jClient.MappingSpec<UUID> map2 = mockMappingSpec();
            Neo4jClient.RecordFetchSpec<UUID> rec2 = mockRecordFetchSpec();

            // First query → findChapterId → returns CHAPTER_ID
            when(neo4jClient.query(anyString())).thenReturn(spec1, spec2);
            when(spec1.bind(any())).thenReturn(bind1);
            when(bind1.to(anyString())).thenReturn(spec1);
            when(spec1.fetchAs(UUID.class)).thenReturn(map1);
            when(map1.mappedBy(any())).thenReturn(rec1);
            when(rec1.one()).thenReturn(Optional.of(CHAPTER_ID));

            // Second query → findBookId → returns BOOK_ID
            when(spec2.bind(any())).thenReturn(bind2);
            when(bind2.to(anyString())).thenReturn(spec2);
            when(spec2.fetchAs(UUID.class)).thenReturn(map2);
            when(map2.mappedBy(any())).thenReturn(rec2);
            when(rec2.one()).thenReturn(Optional.of(BOOK_ID));

            coordinator.recoverStaleTriggers();

            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            StageTriggeredEvent published = eventCaptor.getValue();
            assertThat(published.getBookId()).isEqualTo(BOOK_ID);
            assertThat(published.getStage()).isEqualTo(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION);
        }

        @Test
        @DisplayName("handles empty stale list without publishing events")
        void handlesEmptyList() {
            when(stageRepo.findStaleTriggered(any())).thenReturn(List.of());

            coordinator.recoverStaleTriggers();

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ====================================================================
    //  recoverStaleRunning
    // ====================================================================

    @Nested
    @DisplayName("recoverStaleRunning")
    class RecoverStaleRunning {

        @Test
        @DisplayName("publishes StageTriggered when stage is reset to TRIGGERED")
        void publishesEventWhenResetToTriggered() {
            Stage stale = stageWithStatus(StageKey.CHUNKING, StageStatus.TRIGGERED);

            when(stageRepo.findAndResetStaleRunning(
                    Duration.ofSeconds(STALE_RUNNING_THRESHOLD), MAX_STAGE_ATTEMPTS))
                    .thenReturn(List.of(stale));

            // findChapterId called; CHUNKING is chapter-level so findBookId not called
            stubNeo4jQuery(CHAPTER_ID);

            coordinator.recoverStaleRunning();

            // Stage status is TRIGGERED → event must be published
            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            StageTriggeredEvent published = eventCaptor.getValue();
            assertThat(published.getJobId()).isEqualTo(JOB_ID);
            assertThat(published.getChapterId()).isEqualTo(CHAPTER_ID);
            assertThat(published.getStage()).isEqualTo(StageKey.CHUNKING);
        }

        @Test
        @DisplayName("skips event when stage is reset to FAILED")
        void skipsEventWhenResetToFailed() {
            Stage stale = stageWithStatus(StageKey.EMBEDDING, StageStatus.FAILED);

            when(stageRepo.findAndResetStaleRunning(
                    Duration.ofSeconds(STALE_RUNNING_THRESHOLD), MAX_STAGE_ATTEMPTS))
                    .thenReturn(List.of(stale));

            stubNeo4jQuery(CHAPTER_ID);

            coordinator.recoverStaleRunning();

            // Stage status is FAILED → no event
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("handles empty stale list without publishing events")
        void handlesEmptyList() {
            when(stageRepo.findAndResetStaleRunning(any(), anyInt())).thenReturn(List.of());

            coordinator.recoverStaleRunning();

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ====================================================================
    //  rerunStage
    // ====================================================================

    @Nested
    @DisplayName("rerunStage")
    class RerunStage {

        @Test
        @DisplayName("invalidates downstream, recreates stages, triggers rerun stage")
        void invalidatesDownstreamAndRecreates() {
            stubNeo4jQueryLenient();

            StageKey rerunStage = StageKey.CHUNKING;
            Set<StageKey> invalidated = coordinator.dag().transitiveDownstream(rerunStage);

            // findStageIdsByJobAndSteps — returns a set (the value itself is unused in the method)
            when(stageRepo.findStageIdsByJobAndSteps(JOB_ID, invalidated))
                    .thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));

            // findStageId for each stage in depth-first delete loop
            List<StageKey> byDepth = coordinator.dag().topologicalDepthDescending(invalidated);
            for (StageKey s : byDepth) {
                when(stageRepo.findStageId(JOB_ID, s)).thenReturn(UUID.randomUUID());
            }

            // create for each invalidated stage
            Map<StageKey, UUID> newIds = new java.util.LinkedHashMap<>();
            for (StageKey s : invalidated) {
                UUID newId = UUID.randomUUID();
                newIds.put(s, newId);
                when(stageRepo.create(JOB_ID, s, StageStatus.PENDING)).thenReturn(newId);
            }

            when(stageRepo.tryTrigger(JOB_ID, rerunStage)).thenReturn(true);

            coordinator.rerunStage(JOB_ID, CHAPTER_ID, BOOK_ID, rerunStage);

            // Core interactions verified
            verify(stageRepo).findStageIdsByJobAndSteps(JOB_ID, invalidated);
            verify(stageRepo, times(byDepth.size())).findStageId(eq(JOB_ID), any(StageKey.class));
            verify(stageRepo).deleteByJobIdAndStepIn(JOB_ID, invalidated);
            verify(stageRepo, times(invalidated.size())).create(eq(JOB_ID), any(StageKey.class), eq(StageStatus.PENDING));
            verify(stageRepo).rewireEdges(eq(JOB_ID), any(Map.class), eq(coordinator.dag()));
            verify(stageRepo).tryTrigger(JOB_ID, rerunStage);

            // Event published
            ArgumentCaptor<StageTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(StageTriggeredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            StageTriggeredEvent published = eventCaptor.getValue();
            assertThat(published.getJobId()).isEqualTo(JOB_ID);
            assertThat(published.getChapterId()).isEqualTo(CHAPTER_ID);
            assertThat(published.getBookId()).isEqualTo(BOOK_ID);
            assertThat(published.getStage()).isEqualTo(rerunStage);
        }

        @Test
        @DisplayName("skips event when tryTrigger returns false")
        void skipsEventWhenBarrierNotSatisfied() {
            stubNeo4jQueryLenient();

            StageKey rerunStage = StageKey.CHUNKING;
            Set<StageKey> invalidated = coordinator.dag().transitiveDownstream(rerunStage);

            when(stageRepo.findStageIdsByJobAndSteps(JOB_ID, invalidated)).thenReturn(Set.of());

            // findStageId returns null → deleteDataByStageId is skipped
            for (StageKey s : coordinator.dag().topologicalDepthDescending(invalidated)) {
                when(stageRepo.findStageId(JOB_ID, s)).thenReturn(null);
            }

            for (StageKey s : invalidated) {
                when(stageRepo.create(JOB_ID, s, StageStatus.PENDING)).thenReturn(UUID.randomUUID());
            }

            when(stageRepo.tryTrigger(JOB_ID, rerunStage)).thenReturn(false);

            coordinator.rerunStage(JOB_ID, CHAPTER_ID, BOOK_ID, rerunStage);

            verify(stageRepo).tryTrigger(JOB_ID, rerunStage);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("creates fresh PENDING stages for each invalidated stage")
        void createsFreshPendingStages() {
            stubNeo4jQueryLenient();

            StageKey rerunStage = StageKey.EMBEDDING;
            Set<StageKey> invalidated = coordinator.dag().transitiveDownstream(rerunStage);

            when(stageRepo.findStageIdsByJobAndSteps(JOB_ID, invalidated)).thenReturn(Set.of());
            for (StageKey s : coordinator.dag().topologicalDepthDescending(invalidated)) {
                when(stageRepo.findStageId(JOB_ID, s)).thenReturn(null);
            }
            for (StageKey s : invalidated) {
                when(stageRepo.create(JOB_ID, s, StageStatus.PENDING)).thenReturn(UUID.randomUUID());
            }
            when(stageRepo.tryTrigger(any(), any())).thenReturn(false);

            coordinator.rerunStage(JOB_ID, CHAPTER_ID, BOOK_ID, rerunStage);

            for (StageKey s : invalidated) {
                verify(stageRepo).create(JOB_ID, s, StageStatus.PENDING);
            }
        }

        @Test
        @DisplayName("calls findStageId for each invalidated stage in depth-first order")
        void callsFindStageIdForEachDepthStage() {
            stubNeo4jQueryLenient();

            StageKey rerunStage = StageKey.CHAPTER_EVENT_CONSOLIDATION;
            Set<StageKey> invalidated = coordinator.dag().transitiveDownstream(rerunStage);
            List<StageKey> byDepth = coordinator.dag().topologicalDepthDescending(invalidated);

            when(stageRepo.findStageIdsByJobAndSteps(JOB_ID, invalidated)).thenReturn(Set.of());
            for (StageKey s : byDepth) {
                when(stageRepo.findStageId(JOB_ID, s)).thenReturn(null);
            }
            for (StageKey s : invalidated) {
                when(stageRepo.create(JOB_ID, s, StageStatus.PENDING)).thenReturn(UUID.randomUUID());
            }
            when(stageRepo.tryTrigger(any(), any())).thenReturn(false);

            coordinator.rerunStage(JOB_ID, CHAPTER_ID, BOOK_ID, rerunStage);

            // Verify each stage in the depth-first list had findStageId called
            for (StageKey s : byDepth) {
                verify(stageRepo).findStageId(JOB_ID, s);
            }
        }
    }

    // ====================================================================
    //  dag() accessor
    // ====================================================================

    @Nested
    @DisplayName("dag() accessor")
    class DagAccessor {

        @Test
        @DisplayName("returns a non-null StageDag with correct topology")
        void returnsStageDag() {
            assertThat(coordinator.dag()).isNotNull();
            assertThat(coordinator.dag().roots()).containsExactly(StageKey.SCENE_SEGMENTATION);
            assertThat(coordinator.dag().validateConnectivity()).isEmpty();
        }
    }

    // ====================================================================
    //  Edge cases
    // ====================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles all 16 StageKey values completing successfully")
        void handlesAllStageKeysSuccessfully() {
            stubNeo4jQueryLenient();

            // INGESTION_COMPLETE has no children, so tryTrigger is never called for it.
            // For all other stages, stub tryTrigger to return false for each child.
            for (StageKey stage : StageKey.values()) {
                boolean isBookLevel = stage.isBookLevel();
                UUID bookId = isBookLevel ? BOOK_ID : null;
                StageCompletedEvent event = completedEvent(stage, bookId, successResult(stage));

                // Stub tryTrigger for all children to return false
                for (StageKey child : coordinator.dag().childrenOf(stage)) {
                    when(stageRepo.tryTrigger(JOB_ID, child)).thenReturn(false);
                }

                coordinator.onStageCompleted(event);

                verify(stageRepo).setCompleted(JOB_ID, stage);
            }
        }

        @Test
        @DisplayName("recoverStaleTriggers with multiple stale stages publishes one event per stage")
        void multipleStaleStages() {
            Stage stale1 = stageWithStatus(StageKey.CHUNKING, StageStatus.TRIGGERED);
            Stage stale2 = stageWithStatus(StageKey.EMBEDDING, StageStatus.TRIGGERED);

            when(stageRepo.findStaleTriggered(any())).thenReturn(List.of(stale1, stale2));

            // Both are chapter-level → only findChapterId is called (twice)
            // Need two independent chain mocks for the two query() calls
            Neo4jClient.UnboundRunnableSpec spec1 = mockUnboundRunnableSpec();
            Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> bind1 = mockOngoingBindSpec();
            Neo4jClient.MappingSpec<UUID> map1 = mockMappingSpec();
            Neo4jClient.RecordFetchSpec<UUID> rec1 = mockRecordFetchSpec();

            Neo4jClient.UnboundRunnableSpec spec2 = mockUnboundRunnableSpec();
            Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> bind2 = mockOngoingBindSpec();
            Neo4jClient.MappingSpec<UUID> map2 = mockMappingSpec();
            Neo4jClient.RecordFetchSpec<UUID> rec2 = mockRecordFetchSpec();

            when(neo4jClient.query(anyString())).thenReturn(spec1, spec2);
            when(spec1.bind(any())).thenReturn(bind1);
            when(bind1.to(anyString())).thenReturn(spec1);
            when(spec1.fetchAs(UUID.class)).thenReturn(map1);
            when(map1.mappedBy(any())).thenReturn(rec1);
            when(rec1.one()).thenReturn(Optional.of(CHAPTER_ID));

            when(spec2.bind(any())).thenReturn(bind2);
            when(bind2.to(anyString())).thenReturn(spec2);
            when(spec2.fetchAs(UUID.class)).thenReturn(map2);
            when(map2.mappedBy(any())).thenReturn(rec2);
            when(rec2.one()).thenReturn(Optional.of(CHAPTER_ID));

            coordinator.recoverStaleTriggers();

            verify(eventPublisher, times(2)).publishEvent(any());
        }

        @Test
        @DisplayName("recoverStaleRunning with mixed stages publishes only for TRIGGERED")
        void multipleStaleRunningMixesTriggeredAndFailed() {
            Stage triggered = stageWithStatus(StageKey.CHUNKING, StageStatus.TRIGGERED);
            Stage failed = stageWithStatus(StageKey.EMBEDDING, StageStatus.FAILED);

            when(stageRepo.findAndResetStaleRunning(any(), anyInt()))
                    .thenReturn(List.of(triggered, failed));

            // Two findChapterId calls needed
            Neo4jClient.UnboundRunnableSpec spec1 = mockUnboundRunnableSpec();
            Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> bind1 = mockOngoingBindSpec();
            Neo4jClient.MappingSpec<UUID> map1 = mockMappingSpec();
            Neo4jClient.RecordFetchSpec<UUID> rec1 = mockRecordFetchSpec();

            Neo4jClient.UnboundRunnableSpec spec2 = mockUnboundRunnableSpec();
            Neo4jClient.OngoingBindSpec<Object, Neo4jClient.RunnableSpec> bind2 = mockOngoingBindSpec();
            Neo4jClient.MappingSpec<UUID> map2 = mockMappingSpec();
            Neo4jClient.RecordFetchSpec<UUID> rec2 = mockRecordFetchSpec();

            when(neo4jClient.query(anyString())).thenReturn(spec1, spec2);
            when(spec1.bind(any())).thenReturn(bind1);
            when(bind1.to(anyString())).thenReturn(spec1);
            when(spec1.fetchAs(UUID.class)).thenReturn(map1);
            when(map1.mappedBy(any())).thenReturn(rec1);
            when(rec1.one()).thenReturn(Optional.of(CHAPTER_ID));

            when(spec2.bind(any())).thenReturn(bind2);
            when(bind2.to(anyString())).thenReturn(spec2);
            when(spec2.fetchAs(UUID.class)).thenReturn(map2);
            when(map2.mappedBy(any())).thenReturn(rec2);
            when(rec2.one()).thenReturn(Optional.of(CHAPTER_ID));

            coordinator.recoverStaleRunning();

            // Only 1 event for TRIGGERED, not for FAILED
            verify(eventPublisher, times(1)).publishEvent(any());
        }

        @Test
        @DisplayName("onStageCompleted with book-level stage completing does not look up chapterId")
        void bookLevelCompletionNoChapterLookup() {
            // Book-level stages have a non-null bookId in the event → resolveBookId
            // uses it directly for children. No Neo4jClient queries needed.
            stubNeo4jQueryLenient();

            StageKey stage = StageKey.BOOK_INDIVIDUAL_CONSOLIDATION;
            StageCompletedEvent event = completedEvent(stage, BOOK_ID, successResult(stage));

            when(stageRepo.tryTrigger(JOB_ID, StageKey.INGESTION_COMPLETE)).thenReturn(false);

            coordinator.onStageCompleted(event);

            verify(neo4jClient, never()).query(anyString());
        }
    }
}
