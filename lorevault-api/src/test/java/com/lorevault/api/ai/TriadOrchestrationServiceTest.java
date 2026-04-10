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
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        // Arrange
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = createTestTriads();
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-detection-pass2")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(sceneDetectionClient.detectScenesPass2Triad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        // Act
        List<TriadOrchestrationService.TriadAnalysis> result = 
            triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        // Assert
        assertThat(result).hasSize(2);

        // Verify status updates were called for each triad
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

        // Verify first triad status record
        assertThat(capturedJobIds.get(0)).isEqualTo(testJobId);
        assertThat(capturedStatuses.get(0)).isEqualTo(IngestionStatus.SCENE_TRIAD_ANALYSIS);
        assertThat(capturedDescriptions.get(0)).isEqualTo("Triad analysis for scenes [prev, curr, next]");
        
        Map<String, Object> firstTriadProps = capturedProperties.get(0);
        assertThat(firstTriadProps).containsEntry("triadIndex", 0);
        assertThat(firstTriadProps).containsEntry("prevSceneId", null);
        assertThat(firstTriadProps).containsEntry("currentSceneId", scene1Id);
        assertThat(firstTriadProps).containsEntry("nextSceneId", scene2Id);

        // Verify second triad status record
        assertThat(capturedJobIds.get(1)).isEqualTo(testJobId);
        assertThat(capturedStatuses.get(1)).isEqualTo(IngestionStatus.SCENE_TRIAD_ANALYSIS);
        assertThat(capturedDescriptions.get(1)).isEqualTo("Triad analysis for scenes [prev, curr, next]");
        
        Map<String, Object> secondTriadProps = capturedProperties.get(1);
        assertThat(secondTriadProps).containsEntry("triadIndex", 1);
        assertThat(secondTriadProps).containsEntry("prevSceneId", scene1Id);
        assertThat(secondTriadProps).containsEntry("currentSceneId", scene2Id);
        assertThat(secondTriadProps).containsEntry("nextSceneId", scene3Id);
    }

    @Test
    @DisplayName("Should handle empty triads list gracefully")
    void shouldHandleEmptyTriadsListGracefully() {
        // Arrange
        Chapter testChapter = createTestChapter();
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(List.of());

        // Act
        List<TriadOrchestrationService.TriadAnalysis> result = 
            triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        // Assert
        assertThat(result).isEmpty();
        verify(ingestionJobService, never()).updateJobStatus(any(), any(), any(), any());
        verify(sceneDetectionClient, never()).detectScenesPass2Triad(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should call SceneDetectionClient after creating status record")
    void shouldCallSceneDetectionClientAfterCreatingStatusRecord() {
        // Arrange
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createSingleTriad());
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-detection-pass2")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(sceneDetectionClient.detectScenesPass2Triad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        // Act
        triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        // Assert - verify order: status update first, then LLM call
        var inOrder = inOrder(ingestionJobService, sceneDetectionClient);
        inOrder.verify(ingestionJobService).updateJobStatus(any(), any(), any(), any());
        inOrder.verify(sceneDetectionClient).detectScenesPass2Triad(
                eq(testJobId),
                eq("mock system prompt"),
                any(),
                eq(TriadOrchestrationService.TriadStructuredResult.class)
        );
    }

    @Test
    @DisplayName("Should include proper user variables for triad LLM call")
    void shouldIncludeProperUserVariablesForTriadLlmCall() {
        // Arrange
        Chapter testChapter = createTestChapterWithText();
        List<TriadBuilderService.SceneTriad> triads = List.of(createSingleTriad());
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-detection-pass2")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(sceneDetectionClient.detectScenesPass2Triad(any(), any(), any(), eq(TriadOrchestrationService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        // Capture the user variables passed to the LLM client
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> userVarsCaptor = ArgumentCaptor.forClass(Map.class);

        // Act
        triadOrchestrationService.analyzeChapterTriads(testJobId, testChapter);

        // Assert
        verify(sceneDetectionClient).detectScenesPass2Triad(
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

    private Chapter createTestChapter() {
        Chapter chapter = new Chapter();
        BeanWrapperImpl chapterBean = new BeanWrapperImpl(chapter);
        chapterBean.setPropertyValue("id", testChapterId);
        chapterBean.setPropertyValue("rawText", "Sample chapter text for testing purposes.");
        return chapter;
    }

    private Chapter createTestChapterWithText() {
        Chapter chapter = new Chapter();
        BeanWrapperImpl chapterBean = new BeanWrapperImpl(chapter);
        chapterBean.setPropertyValue("id", testChapterId);
        chapterBean.setPropertyValue("rawText", "This is scene one text. This is scene two text. This is scene three text.");
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
            "IMMEDIATE_SUCCESSION", "HIGH", "Scene transition analysis"
        );
        TriadOrchestrationService.TriadRelation mockCurrToNext = new TriadOrchestrationService.TriadRelation(
            "CONCURRENT", "MEDIUM", "Overlapping events"
        );
        
        return new TriadOrchestrationService.TriadStructuredResult(
            "Timeline marker: Chapter 1",
            mockPrevToCurr,
            mockCurrToNext
        );
    }
}
