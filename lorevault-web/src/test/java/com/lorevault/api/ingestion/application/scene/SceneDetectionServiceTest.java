package com.lorevault.api.ingestion.application.scene;
import com.lorevault.api.ai.domain.LlmRetryStrategy;
import com.lorevault.api.ai.domain.SceneDetectionResult;
import com.lorevault.api.ai.domain.SceneLocalizationException;
import com.lorevault.api.ai.domain.SceneWithCoordinates;
import com.lorevault.api.ai.infrastructure.SceneDetectionClient;
import com.lorevault.api.ai.application.TriadOrchestrationService;

import com.lorevault.api.content.entities.Chapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        assertThat(outcome.triadAnalyses()).isEmpty();
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
        assertThat(outcome.triadAnalyses()).isEmpty();
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
                .hasMessageContaining("Scene coordinate localization dropped scenes");

        verify(sceneDetectionClient, times(4)).detectChapterSegmentation(eq(jobId), eq(chapterText));
    }

    @Test
    @DisplayName("Should retry when localization drops any parsed scene")
    void shouldRetryWhenLocalizationDropsAnyParsedScene() {
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

        assertThatThrownBy(() -> sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Scene detection failed with retry")
                .hasMessageContaining("Scene coordinate localization dropped scenes");

        verify(sceneDetectionClient, times(4)).detectChapterSegmentation(eq(jobId), eq(chapterText));
    }

    @Test
    @DisplayName("Should retry when a segment produces no localizable scenes")
    void shouldRetryWhenSegmentProducesNoLocalizableScenes() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Paragraph one sentence one.\n\nParagraph two sentence two.".repeat(40);

        SceneDetectionClient.SegmentationBudgetCheck admission = new SceneDetectionClient.SegmentationBudgetCheck(
                "nlp-big", 400, 100, 20, 180, 200, false
        );

        when(sceneDetectionClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(sceneDetectionClient.detectChapterSegmentation(eq(jobId), any(String.class))).thenReturn(
                "<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>"
        );
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(0, "a", "ctx", "", "", "", "")));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any())).thenReturn(List.of());

        assertThatThrownBy(() -> sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Scene detection failed with retry")
                .hasMessageContaining("Scene coordinate localization returned empty results");

        verify(sceneDetectionClient, times(4)).detectChapterSegmentation(eq(jobId), any(String.class));
    }

    @Test
    @DisplayName("Should preserve scene localization business failure after retries are exhausted")
    void shouldPreserveSceneLocalizationExceptionAfterRetryExhaustion() {
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
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(4, "anchor", "ctx", "", "", "", "")));

        SceneLocalizationException failure = new SceneLocalizationException(
                com.lorevault.api.ingestion.domain.IngestionFailure.builder(
                                "SCENE_LOCALIZATION_ANCHOR_NOT_FOUND",
                                "Failed to localize scene 4 because start anchor 'anchor' was not found"
                        )
                        .exceptionType(SceneLocalizationException.class.getSimpleName())
                        .stage("SCENE_SEGMENTATION")
                        .detail("sceneIndex", 4)
                        .detail("startAnchor", "anchor")
                        .build()
        );

        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any())).thenThrow(failure);

        assertThatThrownBy(() -> sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText))
                .isInstanceOf(SceneLocalizationException.class)
                .hasMessageContaining("start anchor 'anchor' was not found");

        verify(sceneDetectionClient, times(4)).detectChapterSegmentation(eq(jobId), eq(chapterText));
    }

    @Test
    @DisplayName("Should preserve chapter metadata for triad analysis")
    void shouldPreserveChapterMetadataForTriadAnalysis() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        String chapterText = "Short text.";

        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setChapterNumber(3);
        chapter.setRawText(chapterText);

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

        sceneDetectionService.detectScenesInChapter(jobId, chapter);

        ArgumentCaptor<Chapter> chapterCaptor = ArgumentCaptor.forClass(Chapter.class);
        verify(triadOrchestrationService).analyzeChapterTriadsWithIndividuals(eq(jobId), chapterCaptor.capture());

        Chapter capturedChapter = chapterCaptor.getValue();
        assertThat(capturedChapter.getId()).isEqualTo(chapterId);
        assertThat(capturedChapter.getBookId()).isEqualTo(bookId);
        assertThat(capturedChapter.getChapterNumber()).isEqualTo(3);
        assertThat(capturedChapter.getRawText()).isEqualTo(chapterText);
    }
}
