package com.lorevault.api.ingestion.orchestration;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.lorevault.api.ingestion.pipeline.StageKey.BOOK_COLLECTIVE_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.BOOK_EVENT_CANDIDATE_GENERATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.BOOK_INDIVIDUAL_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.BOOK_LOCATION_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.BOOK_OBJECT_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.CHAPTER_EVENT_EMBEDDING;
import static com.lorevault.api.ingestion.pipeline.StageKey.CHAPTER_EVENT_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.CHAPTER_LOCATION_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.CHAPTER_OBJECT_CONSOLIDATION;
import static com.lorevault.api.ingestion.pipeline.StageKey.CHUNKING;
import static com.lorevault.api.ingestion.pipeline.StageKey.EMBEDDING;
import static com.lorevault.api.ingestion.pipeline.StageKey.INGESTION_COMPLETE;
import static com.lorevault.api.ingestion.pipeline.StageKey.SCENE_SEGMENTATION;
import static com.lorevault.api.ingestion.pipeline.StepResult.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StageDispatcher")
class StageDispatcherTest {

    // ── Deterministic test IDs ──────────────────────────────────────────

    private static final UUID JOB_ID = new UUID(0, 1);
    private static final UUID CHAPTER_ID = new UUID(0, 2);
    private static final UUID BOOK_ID = new UUID(0, 3);

    // ── Mocked dependencies ─────────────────────────────────────────────

    @Mock
    private StageGraphRepository stageRepo;
    @Mock
    private StageOutputGraphRepository stageOutputRepo;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<StageCompletedEvent> completedEventCaptor;

    // ── Spied executors (run synchronously for predictable test flow) ───

    private TaskExecutor sceneDetectionTaskExecutor;
    private TaskExecutor ingestionLaneTaskExecutor;

    // ── Synchronous executor (runs command inline) ──────────────────────

    static class SynchronousTaskExecutor implements TaskExecutor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    // ── Annotated handler stubs for production-constructor tests ─────────
    // Each maps to exactly one StageKey so we can build a complete handler
    // list that survives startup validation.

    @ForStage(SCENE_SEGMENTATION) static class H_SCENE_SEGMENTATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(CHUNKING) static class H_CHUNKING implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(EMBEDDING) static class H_EMBEDDING implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(CHAPTER_INDIVIDUAL_CONSOLIDATION) static class H_CHAPTER_INDIVIDUAL_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(CHAPTER_COLLECTIVE_CONSOLIDATION) static class H_CHAPTER_COLLECTIVE_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(CHAPTER_LOCATION_CONSOLIDATION) static class H_CHAPTER_LOCATION_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(CHAPTER_OBJECT_CONSOLIDATION) static class H_CHAPTER_OBJECT_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(CHAPTER_EVENT_CONSOLIDATION) static class H_CHAPTER_EVENT_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(BOOK_INDIVIDUAL_CONSOLIDATION) static class H_BOOK_INDIVIDUAL_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(BOOK_COLLECTIVE_CONSOLIDATION) static class H_BOOK_COLLECTIVE_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(BOOK_LOCATION_CONSOLIDATION) static class H_BOOK_LOCATION_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(BOOK_OBJECT_CONSOLIDATION) static class H_BOOK_OBJECT_CONSOLIDATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(CHAPTER_EVENT_EMBEDDING) static class H_CHAPTER_EVENT_EMBEDDING implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(BOOK_EVENT_CANDIDATE_GENERATION) static class H_BOOK_EVENT_CANDIDATE_GENERATION implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }
    @ForStage(INGESTION_COMPLETE) static class H_INGESTION_COMPLETE implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "ok", 0L); }
    }

    /** A handler with no {@code @ForStage} annotation — used for the "missing @ForStage" test. */
    static class UnannotatedHandler implements StageOperation {
        @Override public StepResult execute(DispatchContext c) { return success(c.stage(), "unannotated", 0L); }
    }

    // ── Set-up ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        sceneDetectionTaskExecutor = spy(new SynchronousTaskExecutor());
        ingestionLaneTaskExecutor = spy(new SynchronousTaskExecutor());

        // Default stubs: let dispatch proceed past guard and idempotency checks
        lenient().when(stageRepo.setRunningConditionally(any(), any())).thenReturn(true);
        lenient().when(stageOutputRepo.existsByChapterIdAndStep(any(), any())).thenReturn(false);
        lenient().when(stageOutputRepo.existsByBookIdAndStep(any(), any())).thenReturn(false);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Uses the package-private test constructor (takes a pre-built handler map). */
    private StageDispatcher createDispatcher(Map<StageKey, StageOperation> handlers) {
        return new StageDispatcher(
                handlers, stageRepo, stageOutputRepo, eventPublisher,
                sceneDetectionTaskExecutor, ingestionLaneTaskExecutor
        );
    }

    /** Uses the public production constructor (takes a handler list, performs annotation scanning). */
    private StageDispatcher createProductionDispatcher(List<StageOperation> handlerList) {
        return new StageDispatcher(
                handlerList, stageRepo, stageOutputRepo, eventPublisher,
                sceneDetectionTaskExecutor, ingestionLaneTaskExecutor
        );
    }

    /** Builds an EnumMap where every StageKey maps to a simple success lambda. */
    private static Map<StageKey, StageOperation> allSuccessHandlers() {
        var map = new EnumMap<StageKey, StageOperation>(StageKey.class);
        for (StageKey key : StageKey.values()) {
            map.put(key, ctx -> success(ctx.stage(), "ok", 0L));
        }
        return map;
    }

    /** Convenience: all annotated handler singletons in declaration order. */
    private static List<StageOperation> allAnnotatedHandlers() {
        return List.of(
                new H_SCENE_SEGMENTATION(),
                new H_CHUNKING(),
                new H_EMBEDDING(),
                new H_CHAPTER_INDIVIDUAL_CONSOLIDATION(),
                new H_CHAPTER_COLLECTIVE_CONSOLIDATION(),
                new H_CHAPTER_LOCATION_CONSOLIDATION(),
                new H_CHAPTER_OBJECT_CONSOLIDATION(),
                new H_CHAPTER_EVENT_CONSOLIDATION(),
                new H_BOOK_INDIVIDUAL_CONSOLIDATION(),
                new H_BOOK_COLLECTIVE_CONSOLIDATION(),
                new H_BOOK_LOCATION_CONSOLIDATION(),
                new H_BOOK_OBJECT_CONSOLIDATION(),
                new H_CHAPTER_EVENT_EMBEDDING(),
                new H_BOOK_EVENT_CANDIDATE_GENERATION(),
                new H_INGESTION_COMPLETE()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Constructor validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("constructor: handler without @ForStage is skipped with warning, construction succeeds")
    void constructor_handlerMissingForStage_shouldLogWarningAndSkip() {
        var handlers = new ArrayList<>(allAnnotatedHandlers());
        handlers.add(new UnannotatedHandler());

        // Should not throw — all 15 keys covered by annotated handlers, extra is silently skipped
        var dispatcher = createProductionDispatcher(handlers);
        assertThat(dispatcher).isNotNull();
    }

    @Test
    @DisplayName("constructor: duplicate @ForStage throws IllegalStateException")
    void constructor_duplicateForStage_shouldThrowIllegalStateException() {
        // Provide only 14 unique + 2 of the same key (INGESTION_COMPLETE duplicated)
        var handlers = new ArrayList<>(allAnnotatedHandlers());
        // Remove the last handler (INGESTION_COMPLETE) and add two copies of H_INGESTION_COMPLETE
        handlers.remove(handlers.size() - 1); // remove H_INGESTION_COMPLETE
        handlers.add(new H_INGESTION_COMPLETE());
        handlers.add(new H_INGESTION_COMPLETE()); // duplicate

        assertThatThrownBy(() -> createProductionDispatcher(handlers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("INGESTION_COMPLETE");
    }

    @Test
    @DisplayName("constructor: missing handler for a StageKey throws IllegalStateException")
    void constructor_missingHandlerForStageKey_shouldThrowIllegalStateException() {
        // Omit H_INGESTION_COMPLETE — only 14 handlers for 15 keys
        var fourteen = allAnnotatedHandlers().subList(0, 14);

        assertThatThrownBy(() -> createProductionDispatcher(fourteen))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No @ForStage handler registered for stage:")
                .hasMessageContaining("INGESTION_COMPLETE");
    }

    @Test
    @DisplayName("constructor: all StageKeys covered — construction succeeds")
    void constructor_allStageKeysCovered_shouldConstructSuccessfully() {
        var dispatcher = createProductionDispatcher(allAnnotatedHandlers());
        assertThat(dispatcher).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Executor routing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("onTrigger: SCENE_SEGMENTATION uses sceneDetectionTaskExecutor")
    void onTrigger_sceneSegmentation_shouldRouteToSceneDetectionExecutor() {
        var dispatcher = createDispatcher(allSuccessHandlers());
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, null, SCENE_SEGMENTATION));

        verify(sceneDetectionTaskExecutor).execute(any());
        verifyNoInteractions(ingestionLaneTaskExecutor);
    }

    @Test
    @DisplayName("onTrigger: non-SCENE_SEGMENTATION stage uses ingestionLaneTaskExecutor")
    void onTrigger_nonSceneSegmentation_shouldRouteToIngestionLaneExecutor() {
        var dispatcher = createDispatcher(allSuccessHandlers());
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(ingestionLaneTaskExecutor).execute(any());
        verifyNoInteractions(sceneDetectionTaskExecutor);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Guard (setRunningConditionally)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dispatch: guard returns false → handler not called, no event published")
    void dispatch_guardReturnsFalse_shouldAbortWithoutCallingHandler() {
        when(stageRepo.setRunningConditionally(JOB_ID, CHUNKING)).thenReturn(false);

        var handler = mock(StageOperation.class);
        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(stageRepo).setRunningConditionally(JOB_ID, CHUNKING);
        verifyNoInteractions(handler);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("dispatch: guard returns true → handler is called")
    void dispatch_guardReturnsTrue_shouldProceedToHandler() {
        when(stageRepo.setRunningConditionally(JOB_ID, CHUNKING)).thenReturn(true);

        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(success(CHUNKING, "done", 0L));

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(handler).execute(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Idempotency — chapter stages
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dispatch: chapter stage with existing output → setSkipped, emit skip event, handler not called")
    void dispatch_chapterStage_outputExists_shouldSkipAndEmitCompletedEvent() {
        when(stageOutputRepo.existsByChapterIdAndStep(CHAPTER_ID, CHUNKING)).thenReturn(true);

        var handler = mock(StageOperation.class);
        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(stageRepo).setSkipped(JOB_ID, CHUNKING);
        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        verifyNoInteractions(handler);

        var event = completedEventCaptor.getValue();
        assertThat(event.getJobId()).isEqualTo(JOB_ID);
        assertThat(event.getChapterId()).isEqualTo(CHAPTER_ID);
        assertThat(event.getBookId()).isNull();
        assertThat(event.getStage()).isEqualTo(CHUNKING);
        assertThat(event.getResult().success()).isTrue();
        assertThat(event.getResult().summary()).contains("Skipped");
    }

    @Test
    @DisplayName("dispatch: chapter stage with no existing output → handler called")
    void dispatch_chapterStage_outputNotExists_shouldCallHandler() {
        when(stageOutputRepo.existsByChapterIdAndStep(CHAPTER_ID, CHUNKING)).thenReturn(false);

        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(success(CHUNKING, "executed", 0L));

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(handler).execute(any());
        verify(eventPublisher).publishEvent(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Idempotency — book stages
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dispatch: book stage with existing output → setSkipped, emit skip event, handler not called")
    void dispatch_bookStage_outputExists_shouldSkipAndEmitCompletedEvent() {
        when(stageOutputRepo.existsByBookIdAndStep(BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION)).thenReturn(true);

        var handler = mock(StageOperation.class);
        var dispatcher = createDispatcher(Map.of(BOOK_INDIVIDUAL_CONSOLIDATION, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(
                this, JOB_ID, CHAPTER_ID, BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION));

        verify(stageRepo).setSkipped(JOB_ID, BOOK_INDIVIDUAL_CONSOLIDATION);
        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        verifyNoInteractions(handler);

        var event = completedEventCaptor.getValue();
        assertThat(event.getJobId()).isEqualTo(JOB_ID);
        assertThat(event.getChapterId()).isEqualTo(CHAPTER_ID);
        assertThat(event.getBookId()).isEqualTo(BOOK_ID);
        assertThat(event.getStage()).isEqualTo(BOOK_INDIVIDUAL_CONSOLIDATION);
        assertThat(event.getResult().success()).isTrue();
        assertThat(event.getResult().summary()).contains("Skipped");
    }

    @Test
    @DisplayName("dispatch: book stage with no existing output → handler called")
    void dispatch_bookStage_outputNotExists_shouldCallHandler() {
        when(stageOutputRepo.existsByBookIdAndStep(BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION)).thenReturn(false);

        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(success(BOOK_INDIVIDUAL_CONSOLIDATION, "executed", 0L));

        var dispatcher = createDispatcher(Map.of(BOOK_INDIVIDUAL_CONSOLIDATION, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(
                this, JOB_ID, CHAPTER_ID, BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION));

        verify(handler).execute(any());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("dispatch: book stage with null bookId → skip idempotency check, handler called")
    void dispatch_bookStage_bookIdNull_shouldSkipIdempotencyCheckAndCallHandler() {
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(success(BOOK_INDIVIDUAL_CONSOLIDATION, "executed", 0L));

        var dispatcher = createDispatcher(Map.of(BOOK_INDIVIDUAL_CONSOLIDATION, handler));
        // Create event with bookId = null (use 4-arg convenience constructor)
        dispatcher.onTrigger(new StageTriggeredEvent(
                this, JOB_ID, CHAPTER_ID, BOOK_INDIVIDUAL_CONSOLIDATION));

        verify(stageOutputRepo, never()).existsByBookIdAndStep(any(), any());
        verify(handler).execute(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Handler execution
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dispatch: handler returns success → StageCompletedEvent published with successful result")
    void dispatch_handlerReturnsSuccess_shouldPublishCompletedEvent() {
        var result = success(CHUNKING, "all good", 42L);
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(result);

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        var event = completedEventCaptor.getValue();
        assertThat(event.getResult()).isSameAs(result);
        assertThat(event.getResult().success()).isTrue();
    }

    @Test
    @DisplayName("dispatch: handler returns failure → StageCompletedEvent published with failure result")
    void dispatch_handlerReturnsFailure_shouldPublishCompletedEvent() {
        var result = StepResult.failure(CHUNKING, "something went wrong", 7L);
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(result);

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        var event = completedEventCaptor.getValue();
        assertThat(event.getResult()).isSameAs(result);
        assertThat(event.getResult().success()).isFalse();
    }

    @Test
    @DisplayName("dispatch: handler throws exception → StageCompletedEvent published with failure result")
    void dispatch_handlerThrowsException_shouldPublishFailureEvent() {
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenThrow(new RuntimeException("critical error"));

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        var event = completedEventCaptor.getValue();
        assertThat(event.getResult().success()).isFalse();
        assertThat(event.getResult().summary()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  MDC context
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dispatch: MDC 'stage' and 'jobId' are set before handler execution")
    void dispatch_shouldSetMdcBeforeHandler() {
        try (MockedStatic<MDC> mdc = mockStatic(MDC.class)) {
            var handler = mock(StageOperation.class);
            when(handler.execute(any())).thenReturn(success(CHUNKING, "done", 0L));

            var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
            dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

            mdc.verify(() -> MDC.put("stage", CHUNKING.name()));
            mdc.verify(() -> MDC.put("jobId", JOB_ID.toString()));
        }
    }

    @Test
    @DisplayName("dispatch: MDC is cleared after successful handler execution")
    void dispatch_shouldClearMdcAfterSuccess() {
        try (MockedStatic<MDC> mdc = mockStatic(MDC.class)) {
            var handler = mock(StageOperation.class);
            when(handler.execute(any())).thenReturn(success(CHUNKING, "done", 0L));

            var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
            dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

            mdc.verify(MDC::clear);
        }
    }

    @Test
    @DisplayName("dispatch: MDC is cleared when guard returns false")
    void dispatch_shouldClearMdcAfterGuardFalse() {
        when(stageRepo.setRunningConditionally(JOB_ID, CHUNKING)).thenReturn(false);

        try (MockedStatic<MDC> mdc = mockStatic(MDC.class)) {
            var handler = mock(StageOperation.class);
            var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
            dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

            mdc.verify(MDC::clear);
            verifyNoInteractions(handler);
        }
    }

    @Test
    @DisplayName("dispatch: MDC is cleared after idempotency skip")
    void dispatch_shouldClearMdcAfterIdempotencySkip() {
        when(stageOutputRepo.existsByChapterIdAndStep(CHAPTER_ID, CHUNKING)).thenReturn(true);

        try (MockedStatic<MDC> mdc = mockStatic(MDC.class)) {
            var handler = mock(StageOperation.class);
            var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
            dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

            mdc.verify(MDC::clear);
            verifyNoInteractions(handler);
        }
    }

    @Test
    @DisplayName("dispatch: MDC is cleared after handler throws exception")
    void dispatch_shouldClearMdcAfterHandlerException() {
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenThrow(new RuntimeException("fail"));

        try (MockedStatic<MDC> mdc = mockStatic(MDC.class)) {
            var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
            dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

            mdc.verify(MDC::clear);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  emitComplete — verify event fields
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("emitComplete: chapter-level stage — event has correct jobId, chapterId, null bookId, stage, result")
    void emitComplete_chapterStage_shouldPublishEventWithCorrectFields() {
        var expectedResult = success(CHUNKING, "chapter done", 5L);
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(expectedResult);

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        var event = completedEventCaptor.getValue();
        assertThat(event.getSource()).isSameAs(dispatcher);
        assertThat(event.getJobId()).isEqualTo(JOB_ID);
        assertThat(event.getChapterId()).isEqualTo(CHAPTER_ID);
        assertThat(event.getBookId()).isNull();
        assertThat(event.getStage()).isEqualTo(CHUNKING);
        assertThat(event.getResult()).isSameAs(expectedResult);
    }

    @Test
    @DisplayName("emitComplete: book-level stage — event has correct jobId, chapterId, bookId, stage, result")
    void emitComplete_bookStage_shouldPublishEventWithCorrectFields() {
        var expectedResult = success(BOOK_INDIVIDUAL_CONSOLIDATION, "book done", 10L);
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(expectedResult);

        var dispatcher = createDispatcher(Map.of(BOOK_INDIVIDUAL_CONSOLIDATION, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(
                this, JOB_ID, CHAPTER_ID, BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        var event = completedEventCaptor.getValue();
        assertThat(event.getSource()).isSameAs(dispatcher);
        assertThat(event.getJobId()).isEqualTo(JOB_ID);
        assertThat(event.getChapterId()).isEqualTo(CHAPTER_ID);
        assertThat(event.getBookId()).isEqualTo(BOOK_ID);
        assertThat(event.getStage()).isEqualTo(BOOK_INDIVIDUAL_CONSOLIDATION);
        assertThat(event.getResult()).isSameAs(expectedResult);
    }

    @Test
    @DisplayName("emitComplete: skip event for chapter stage has null bookId")
    void emitComplete_chapterStageSkipEvent_hasNullBookId() {
        when(stageOutputRepo.existsByChapterIdAndStep(CHAPTER_ID, CHUNKING)).thenReturn(true);

        var handler = mock(StageOperation.class);
        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        assertThat(completedEventCaptor.getValue().getBookId()).isNull();
    }

    @Test
    @DisplayName("emitComplete: skip event for book stage has correct bookId")
    void emitComplete_bookStageSkipEvent_hasCorrectBookId() {
        when(stageOutputRepo.existsByBookIdAndStep(BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION)).thenReturn(true);

        var handler = mock(StageOperation.class);
        var dispatcher = createDispatcher(Map.of(BOOK_INDIVIDUAL_CONSOLIDATION, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(
                this, JOB_ID, CHAPTER_ID, BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        assertThat(completedEventCaptor.getValue().getBookId()).isEqualTo(BOOK_ID);
    }

    @Test
    @DisplayName("emitComplete: handler result duration is preserved in the event")
    void emitComplete_handlerResultDuration_shouldBePreserved() {
        var result = success(CHUNKING, "timely", 1234L);
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(result);

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        verify(eventPublisher).publishEvent(completedEventCaptor.capture());
        assertThat(completedEventCaptor.getValue().getResult().durationMs()).isEqualTo(1234L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Dispatch context propagation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dispatch: handler receives DispatchContext with correct jobId, chapterId, null bookId, stage")
    void dispatch_chapterStage_contextHasCorrectFields() {
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(success(CHUNKING, "ok", 0L));

        var dispatcher = createDispatcher(Map.of(CHUNKING, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, CHUNKING));

        var ctxCaptor = ArgumentCaptor.forClass(DispatchContext.class);
        verify(handler).execute(ctxCaptor.capture());
        var ctx = ctxCaptor.getValue();
        assertThat(ctx.jobId()).isEqualTo(JOB_ID);
        assertThat(ctx.chapterId()).isEqualTo(CHAPTER_ID);
        assertThat(ctx.bookId()).isNull();
        assertThat(ctx.stage()).isEqualTo(CHUNKING);
    }

    @Test
    @DisplayName("dispatch: handler receives DispatchContext with bookId for book-level stage")
    void dispatch_bookStage_contextHasBookId() {
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(success(BOOK_INDIVIDUAL_CONSOLIDATION, "ok", 0L));

        var dispatcher = createDispatcher(Map.of(BOOK_INDIVIDUAL_CONSOLIDATION, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(
                this, JOB_ID, CHAPTER_ID, BOOK_ID, BOOK_INDIVIDUAL_CONSOLIDATION));

        var ctxCaptor = ArgumentCaptor.forClass(DispatchContext.class);
        verify(handler).execute(ctxCaptor.capture());
        var ctx = ctxCaptor.getValue();
        assertThat(ctx.jobId()).isEqualTo(JOB_ID);
        assertThat(ctx.chapterId()).isEqualTo(CHAPTER_ID);
        assertThat(ctx.bookId()).isEqualTo(BOOK_ID);
        assertThat(ctx.stage()).isEqualTo(BOOK_INDIVIDUAL_CONSOLIDATION);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INGESTION_COMPLETE edge cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dispatch: INGESTION_COMPLETE is neither chapter nor book stage — no idempotency check")
    void dispatch_ingestionComplete_shouldSkipIdempotencyCheck() {
        var handler = mock(StageOperation.class);
        when(handler.execute(any())).thenReturn(success(INGESTION_COMPLETE, "done", 0L));

        var dispatcher = createDispatcher(Map.of(INGESTION_COMPLETE, handler));
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, INGESTION_COMPLETE));

        verify(stageOutputRepo, never()).existsByChapterIdAndStep(any(), any());
        verify(stageOutputRepo, never()).existsByBookIdAndStep(any(), any());
        verify(handler).execute(any());
    }

    @Test
    @DisplayName("dispatch: INGESTION_COMPLETE uses ingestionLaneTaskExecutor")
    void onTrigger_ingestionComplete_shouldUseIngestionLaneExecutor() {
        var dispatcher = createDispatcher(allSuccessHandlers());
        dispatcher.onTrigger(new StageTriggeredEvent(this, JOB_ID, CHAPTER_ID, INGESTION_COMPLETE));

        verify(ingestionLaneTaskExecutor).execute(any());
    }
}
