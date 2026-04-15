package com.lorevault.api.ai;

import com.lorevault.api.timeline.TriadEdgePersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SceneDetectionService context budget tests")
class SceneDetectionServiceTest {

    @Mock
    private SceneDetectionClient sceneDetectionClient;
    @Mock
    private SceneProcessingService sceneProcessingService;
    @Spy
    private LlmRetryStrategy llmRetryStrategy;
    @Mock
    private TriadOrchestrationService triadOrchestrationService;
    @Mock
    private TriadEdgePersistenceService triadEdgePersistenceService;

    @InjectMocks
    private SceneDetectionService sceneDetectionService;

    @Test
    @DisplayName("Should use segmented fallback and tag segment edge scenes")
    void shouldUseSegmentedFallbackAndTagSegmentEdges() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Paragraph one sentence one.\n\nParagraph two sentence two.".repeat(40);

        SceneDetectionClient.SegmentationBudgetCheck admission = new SceneDetectionClient.SegmentationBudgetCheck(
                "nlp-big", 400, 100, 20, 180, 200, false
        );

        when(sceneDetectionClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(sceneDetectionClient.detectChapterSegmentation(eq(jobId), any(String.class))).thenReturn("<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>");
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(0, "a", "ctx", "", "", "", "")));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any()))
                .thenReturn(List.of(new SceneWithCoordinates(0, 0, 10, "ctx")));
        when(triadOrchestrationService.analyzeChapterTriadsWithIndividuals(eq(jobId), any()))
                .thenReturn(new TriadOrchestrationService.TriadOutcome(List.of(), List.of(), List.of()));

        SceneDetectionService.SceneDetectionOutcome outcome = sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText);
        List<SceneWithCoordinates> scenes = outcome.scenes();

        assertThat(scenes).hasSize(2);
        assertThat(scenes.get(0).potentialSplitSceneEnd()).isTrue();
        assertThat(scenes.get(0).potentialSplitSceneStart()).isFalse();
        assertThat(scenes.get(1).potentialSplitSceneStart()).isTrue();
        assertThat(scenes.get(1).potentialSplitSceneEnd()).isFalse();
        assertThat(outcome.sceneIndividualExtractions()).isEmpty();

        verify(sceneDetectionClient, times(2)).detectChapterSegmentation(eq(jobId), any(String.class));
    }

    @Test
    @DisplayName("Should use single pass flow when within budget")
    void shouldUseSinglePassWhenWithinBudget() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Short text.";

        SceneDetectionClient.SegmentationBudgetCheck admission = new SceneDetectionClient.SegmentationBudgetCheck(
                "nlp-small", 128000, 89600, 10, 10, 20, true
        );

        when(sceneDetectionClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(sceneDetectionClient.detectChapterSegmentation(eq(jobId), eq(chapterText))).thenReturn("<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>");
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(0, "a", "ctx", "", "", "", "")));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any()))
                .thenReturn(List.of(new SceneWithCoordinates(0, 0, chapterText.length(), "ctx")));
        when(triadOrchestrationService.analyzeChapterTriadsWithIndividuals(eq(jobId), any()))
                .thenReturn(new TriadOrchestrationService.TriadOutcome(List.of(), List.of(), List.of()));

        SceneDetectionService.SceneDetectionOutcome outcome = sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText);
        List<SceneWithCoordinates> scenes = outcome.scenes();

        assertThat(scenes).hasSize(1);
        assertThat(scenes.get(0).potentialSplitSceneStart()).isFalse();
        assertThat(scenes.get(0).potentialSplitSceneEnd()).isFalse();
        assertThat(outcome.sceneIndividualExtractions()).isEmpty();
        verify(sceneDetectionClient, times(1)).detectChapterSegmentation(eq(jobId), eq(chapterText));
    }

    @Test
    @DisplayName("Should retry when localization drops too many parsed scenes")
    void shouldRetryWhenLocalizationDropsTooManyScenes() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Short text.";

        SceneDetectionClient.SegmentationBudgetCheck admission = new SceneDetectionClient.SegmentationBudgetCheck(
                "nlp-small", 128000, 89600, 10, 10, 20, true
        );

        when(sceneDetectionClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(sceneDetectionClient.detectChapterSegmentation(eq(jobId), eq(chapterText))).thenReturn(
                "<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>"
        );
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt())).thenReturn(List.of(
                new SceneDetectionResult(0, "a", "ctx-1", "", "", "", ""),
                new SceneDetectionResult(1, "b", "ctx-2", "", "", "", ""),
                new SceneDetectionResult(2, "c", "ctx-3", "", "", "", ""),
                new SceneDetectionResult(3, "d", "ctx-4", "", "", "", "")
        ));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any()))
                .thenReturn(List.of(new SceneWithCoordinates(0, 0, chapterText.length(), "ctx-1")));

        assertThatThrownBy(() -> sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Scene detection failed with retry")
                .hasMessageContaining("Scene coordinate localization dropped too many scenes");

        verify(sceneDetectionClient, times(4)).detectChapterSegmentation(eq(jobId), eq(chapterText));
    }

    @Test
    @DisplayName("Should tolerate a small amount of localization loss")
    void shouldTolerateSmallLocalizationLoss() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Short text.";

        SceneDetectionClient.SegmentationBudgetCheck admission = new SceneDetectionClient.SegmentationBudgetCheck(
                "nlp-small", 128000, 89600, 10, 10, 20, true
        );

        when(sceneDetectionClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(sceneDetectionClient.detectChapterSegmentation(eq(jobId), eq(chapterText))).thenReturn(
                "<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>"
        );
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt())).thenReturn(List.of(
                new SceneDetectionResult(0, "a", "ctx-1", "", "", "", ""),
                new SceneDetectionResult(1, "b", "ctx-2", "", "", "", ""),
                new SceneDetectionResult(2, "c", "ctx-3", "", "", "", ""),
                new SceneDetectionResult(3, "d", "ctx-4", "", "", "", ""),
                new SceneDetectionResult(4, "e", "ctx-5", "", "", "", "")
        ));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any())).thenReturn(List.of(
                new SceneWithCoordinates(0, 0, 2, "ctx-1"),
                new SceneWithCoordinates(1, 2, 4, "ctx-2"),
                new SceneWithCoordinates(2, 4, 6, "ctx-3"),
                new SceneWithCoordinates(3, 6, 8, "ctx-4")
        ));
        when(triadOrchestrationService.analyzeChapterTriadsWithIndividuals(eq(jobId), any()))
                .thenReturn(new TriadOrchestrationService.TriadOutcome(List.of(), List.of(), List.of()));

        SceneDetectionService.SceneDetectionOutcome outcome = sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText);

        assertThat(outcome.scenes()).hasSize(4);
        verify(sceneDetectionClient, times(1)).detectChapterSegmentation(eq(jobId), eq(chapterText));
    }
}
