package com.lorevault.api.ai;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.ingestion.IngestionStatus;
import com.lorevault.api.ingestion.IngestionJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriadOrchestrationService")
class TriadOrchestrationServiceTest {

    @Mock
    private TriadBuilderService triadBuilderService;
    
    @Mock
    private SceneDetectionClient sceneDetectionClient;

    @Mock
    private PromptRepository promptRepository;
    
    @Mock
    private IngestionJobService ingestionJobService;

    @Captor
    private ArgumentCaptor<UUID> jobIdCaptor;
    
    @Captor
    private ArgumentCaptor<IngestionStatus> statusCaptor;
    
    @Captor
    private ArgumentCaptor<String> descriptionCaptor;
    
    @Captor
    private ArgumentCaptor<Map<String, Object>> propertiesCaptor;

    private TriadOrchestrationService triadOrchestrationService;

    private final UUID testJobId = UUID.randomUUID();
    private final UUID testChapterId = UUID.randomUUID();
    private final UUID scene1Id = UUID.randomUUID();
    private final UUID scene2Id = UUID.randomUUID();
    private final UUID scene3Id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        triadOrchestrationService = new TriadOrchestrationService(
            triadBuilderService,
            sceneDetectionClient,
            promptRepository,
            ingestionJobService
        );
    }

    @Test
    @DisplayName("Should create status record before each triad LLM call with proper metadata")
    void shouldCreateStatusRecordBeforeEachTriadLlmCall() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = createTestTriads();
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        List<TriadOrchestrationService.TriadAnalysis> result = 
            triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).hasSize(2);
        verify(ingestionJobService, times(2)).updateJobStatus(
            jobIdCaptor.capture(),
            statusCaptor.capture(),
            descriptionCaptor.capture(),
            propertiesCaptor.capture()
        );

        List<UUID> capturedJobIds = jobIdCaptor.getAllValues();
        List<IngestionStatus> capturedStatuses = statusCaptor.getAllValues();
        List<String> capturedDescriptions = descriptionCaptor.getAllValues();
        List<Map<String, Object>> capturedProperties = propertiesCaptor.getAllValues();

        assertThat(capturedJobIds.get(0)).isEqualTo(testJobId);
        assertThat(capturedStatuses.get(0)).isEqualTo(IngestionStatus.SCENE_TRIAD_ANALYSIS);
        assertThat(capturedDescriptions.get(0)).isEqualTo("Triad analysis for scenes [prev, curr, next]");
        
        Map<String, Object> firstTriadProps = capturedProperties.get(0);
        assertThat(firstTriadProps).containsEntry("triadIndex", 0);
        assertThat(firstTriadProps).containsEntry("prevSceneIndex", null);
        assertThat(firstTriadProps).containsEntry("currentSceneIndex", 0);
        assertThat(firstTriadProps).containsEntry("nextSceneIndex", 1);

        assertThat(capturedJobIds.get(1)).isEqualTo(testJobId);
        assertThat(capturedStatuses.get(1)).isEqualTo(IngestionStatus.SCENE_TRIAD_ANALYSIS);
        assertThat(capturedDescriptions.get(1)).isEqualTo("Triad analysis for scenes [prev, curr, next]");
        
        Map<String, Object> secondTriadProps = capturedProperties.get(1);
        assertThat(secondTriadProps).containsEntry("triadIndex", 1);
        assertThat(secondTriadProps).containsEntry("prevSceneIndex", 0);
        assertThat(secondTriadProps).containsEntry("currentSceneIndex", 1);
        assertThat(secondTriadProps).containsEntry("nextSceneIndex", 2);
    }

    @Test
    @DisplayName("Should handle empty triads list gracefully")
    void shouldHandleEmptyTriadsListGracefully() {
        Chapter testChapter = createTestChapter();
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(List.of());

        List<TriadOrchestrationService.TriadAnalysis> result = 
            triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).isEmpty();
        verify(ingestionJobService, never()).updateJobStatus(any(), any(), any(), any());
        verify(sceneDetectionClient, never()).detectSceneAnalysisTriad(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should call SceneDetectionClient after creating status record")
    void shouldCallSceneDetectionClientAfterCreatingStatusRecord() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createSingleTriad());
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        var inOrder = inOrder(ingestionJobService, sceneDetectionClient);
        inOrder.verify(ingestionJobService).updateJobStatus(any(), any(), any(), any());
        inOrder.verify(sceneDetectionClient).detectSceneAnalysisTriad(
                eq(testJobId),
                eq("mock system prompt"),
                any(),
                eq(TriadOrchestrationService.TriadStructuredResult.class)
        );
    }

    @Test
    @DisplayName("Should include proper user variables for triad LLM call")
    void shouldIncludeProperUserVariablesForTriadLlmCall() {
        Chapter testChapter = createTestChapterWithText();
        List<TriadBuilderService.SceneTriad> triads = List.of(createSingleTriad());
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> userVarsCaptor = ArgumentCaptor.forClass(Map.class);

        triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        verify(sceneDetectionClient).detectSceneAnalysisTriad(
                eq(testJobId),
                eq("mock system prompt"),
                userVarsCaptor.capture(),
                eq(TriadOrchestrationService.TriadStructuredResult.class)
        );
        
        Map<String, Object> userVars = userVarsCaptor.getValue();
        assertThat(userVars).containsKeys(
            "prev_context_summary", "prev_time_indicators", "prev_break_reason", "prev_text",
            "curr_context_summary", "curr_time_indicators", "curr_break_reason", "curr_text",
            "next_context_summary", "next_time_indicators", "next_break_reason", "next_text"
        );
    }

    @Test
    @DisplayName("Should fail when required previousToCurrent relation is missing")
    void shouldFailWhenPreviousToCurrentRelationMissing() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        TriadOrchestrationService.TriadStructuredResult invalid =
                new TriadOrchestrationService.TriadStructuredResult("marker", null,
                        new TriadOrchestrationService.TriadRelation("BEFORE", "Explicit", "evidence"));

        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(invalid);

        assertThatThrownBy(() -> triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter))
                .isInstanceOf(TriadAnalysisException.class)
                .hasMessageContaining("omitted required relation 'previousToCurrent'");
    }

    @Test
    @DisplayName("Should normalize legacy meets relation to canonical before")
    void shouldNormalizeLegacyMeetsRelationToCanonicalBefore() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        TriadOrchestrationService.TriadStructuredResult legacy =
                new TriadOrchestrationService.TriadStructuredResult(
                        "marker",
                        new TriadOrchestrationService.TriadRelation("R:temporal.meets", "Explicit", "evidence"),
                        null
                );

        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(legacy);

        List<TriadOrchestrationService.TriadAnalysis> result =
                triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).singleElement().satisfies(analysis -> {
            assertThat(analysis.prevToCurrType()).isEqualTo("R:temporal.before");
            assertThat(analysis.currVsPrevInverted()).isEqualTo("R:temporal.after");
        });
    }

    @Test
    @DisplayName("Should preserve during as distinct relation in practical vocabulary")
    void shouldPreserveDuringAsDistinctRelation() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        TriadOrchestrationService.TriadStructuredResult parsed =
                new TriadOrchestrationService.TriadStructuredResult(
                        "marker",
                        new TriadOrchestrationService.TriadRelation("R:temporal.during", "Explicit", "evidence"),
                        null
                );

        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(parsed);

        List<TriadOrchestrationService.TriadAnalysis> result =
                triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).singleElement().satisfies(analysis -> {
            assertThat(analysis.prevToCurrType()).isEqualTo("R:temporal.during");
            assertThat(analysis.currVsPrevInverted()).isEqualTo("R:temporal.contains");
        });
    }

    @Test
    @DisplayName("Should fail when relation type is outside ADR010 practical vocabulary")
    void shouldFailWhenRelationTypeIsOutsideAdr010PracticalVocabulary() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        TriadOrchestrationService.TriadStructuredResult invalid =
                new TriadOrchestrationService.TriadStructuredResult(
                        "marker",
                        new TriadOrchestrationService.TriadRelation("IMMEDIATE_SUCCESSION", "Explicit", "evidence"),
                        null
                );

        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(invalid);

        assertThatThrownBy(() -> triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter))
                .isInstanceOf(TriadAnalysisException.class)
                .hasMessageContaining("unsupported temporalType 'IMMEDIATE_SUCCESSION'");
    }

    @Test
    @DisplayName("Should coarsen legacy equals relation to overlaps")
    void shouldCoarsenLegacyEqualsRelationToOverlaps() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        TriadOrchestrationService.TriadStructuredResult legacy =
                new TriadOrchestrationService.TriadStructuredResult(
                        "marker",
                        new TriadOrchestrationService.TriadRelation("R:temporal.equals", "Explicit", "same moment legacy output"),
                        null
                );

        when(sceneDetectionClient.detectSceneAnalysisTriad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(legacy);

        List<TriadOrchestrationService.TriadAnalysis> result =
                triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).singleElement().satisfies(analysis -> {
            assertThat(analysis.prevToCurrType()).isEqualTo("R:temporal.overlaps");
            assertThat(analysis.currVsPrevInverted()).isEqualTo("R:temporal.overlapped_by");
        });
    }

    private Chapter createTestChapter() {
        Chapter chapter = instantiateWithoutConstructor(Chapter.class);
        setField(chapter, "id", testChapterId);
        setField(chapter, "rawText", "Sample chapter text for testing purposes.");
        return chapter;
    }

    private Chapter createTestChapterWithText() {
        Chapter chapter = instantiateWithoutConstructor(Chapter.class);
        setField(chapter, "id", testChapterId);
        setField(chapter, "rawText", "This is scene one text. This is scene two text. This is scene three text.");
        return chapter;
    }

    private List<TriadBuilderService.SceneTriad> createTestTriads() {
        Scene scene1 = createScene(scene1Id, 0, 0L, 20L);
        Scene scene2 = createScene(scene2Id, 1, 21L, 40L);
        Scene scene3 = createScene(scene3Id, 2, 41L, 60L);

        return List.of(
            new TriadBuilderService.SceneTriad(null, scene1, scene2),
            new TriadBuilderService.SceneTriad(scene1, scene2, scene3)
        );
    }

    private TriadBuilderService.SceneTriad createSingleTriad() {
        Scene scene1 = createScene(scene1Id, 0, 0L, 20L);
        Scene scene2 = createScene(scene2Id, 1, 21L, 40L);
        return new TriadBuilderService.SceneTriad(null, scene1, scene2);
    }

    private TriadBuilderService.SceneTriad createTriadWithPreviousAndCurrent() {
        Scene scene0 = createScene(scene1Id, 0, 0L, 20L);
        Scene scene1 = createScene(scene2Id, 1, 21L, 40L);
        return new TriadBuilderService.SceneTriad(scene0, scene1, null);
    }

    private Scene createScene(UUID id, int index, Long start, Long end) {
        return new Scene(
                id,
                index,
                start,
                end,
                "Context summary for scene " + index,
                null,
                testChapterId,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TriadOrchestrationService.TriadStructuredResult createMockTriadResult() {
        TriadOrchestrationService.TriadRelation mockPrevToCurr = new TriadOrchestrationService.TriadRelation(
            "R:temporal.before", "Explicit", "Scene transition analysis"
        );
        TriadOrchestrationService.TriadRelation mockCurrToNext = new TriadOrchestrationService.TriadRelation(
            "R:temporal.overlaps", "StronglyImplied", "Overlapping events"
        );
        
        return new TriadOrchestrationService.TriadStructuredResult(
            "Timeline marker: Chapter 1",
            mockPrevToCurr,
            mockCurrToNext
        );
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

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
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
