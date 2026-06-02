package com.lorevault.api.graph.event.scene;
import com.lorevault.api.ai.ModelSlot;
import com.lorevault.api.ai.llm.LlmClient;

import com.lorevault.api.orchestration.job.IngestionFailure;
import com.lorevault.api.orchestration.scene.SceneDetectionException;
import com.lorevault.api.orchestration.scene.SceneDetectionResult;
import com.lorevault.api.orchestration.scene.SceneDetectionService;
import com.lorevault.api.orchestration.scene.SceneLocalizationException;
import com.lorevault.api.orchestration.scene.SceneProcessingService;
import com.lorevault.api.orchestration.scene.SceneWithCoordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SceneDetectionService context budget tests")
class SceneDetectionServiceTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private SceneProcessingService sceneProcessingService;
    @InjectMocks
    private SceneDetectionService sceneDetectionService;

    @Test
    @DisplayName("Should use segmented fallback and tag segment edge scenes")
    void shouldUseSegmentedFallbackAndTagSegmentEdges() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Paragraph one sentence one.\n\nParagraph two sentence two.".repeat(40);

        LlmClient.SegmentationBudgetCheck admission = new LlmClient.SegmentationBudgetCheck(
                ModelSlot.NLP_BIG.slotName(), 400, 100, 20, 180, 200, false
        );

        when(llmClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(llmClient.detectChapterSegmentation(eq(jobId), any(String.class), anyDouble())).thenReturn("<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>");
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(0, "a", "ctx", "", "", "", "")));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any()))
                .thenReturn(List.of(new SceneWithCoordinates(0, 0, 10, "ctx")));
        SceneDetectionService.SceneSegmentationOutcome outcome = sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText);
        List<SceneWithCoordinates> scenes = outcome.scenes();

        assertThat(scenes).hasSize(2);
        assertThat(scenes.get(0).potentialSplitSceneEnd()).isTrue();
        assertThat(scenes.get(0).potentialSplitSceneStart()).isFalse();
        assertThat(scenes.get(1).potentialSplitSceneStart()).isTrue();
        assertThat(scenes.get(1).potentialSplitSceneEnd()).isFalse();
        verify(llmClient, times(2)).detectChapterSegmentation(eq(jobId), any(String.class), anyDouble());
    }

    @Test
    @DisplayName("Should use single pass flow when within budget")
    void shouldUseSinglePassWhenWithinBudget() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Short text.";

        LlmClient.SegmentationBudgetCheck admission = new LlmClient.SegmentationBudgetCheck(
                ModelSlot.NLP_SMALL.slotName(), 128000, 89600, 10, 10, 20, true
        );

        when(llmClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(llmClient.detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble())).thenReturn("<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>");
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(0, "a", "ctx", "", "", "", "")));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any()))
                .thenReturn(List.of(new SceneWithCoordinates(0, 0, chapterText.length(), "ctx")));
        SceneDetectionService.SceneSegmentationOutcome outcome = sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText);
        List<SceneWithCoordinates> scenes = outcome.scenes();

        assertThat(scenes).hasSize(1);
        assertThat(scenes.get(0).potentialSplitSceneStart()).isFalse();
        assertThat(scenes.get(0).potentialSplitSceneEnd()).isFalse();
        verify(llmClient, times(1)).detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble());
    }

    @Test
    @DisplayName("Should fail when localization drops too many parsed scenes")
    void shouldFailWhenLocalizationDropsTooManyScenes() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Short text.";

        LlmClient.SegmentationBudgetCheck admission = new LlmClient.SegmentationBudgetCheck(
                ModelSlot.NLP_SMALL.slotName(), 128000, 89600, 10, 10, 20, true
        );

        when(llmClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(llmClient.detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble())).thenReturn(
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
                .isInstanceOf(SceneDetectionException.class)
                .hasMessageContaining("Scene coordinate localization dropped scenes");

        verify(llmClient, times(1)).detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble());
    }

    @Test
    @DisplayName("Should fail when localization drops any parsed scene")
    void shouldFailWhenLocalizationDropsAnyParsedScene() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Short text.";

        LlmClient.SegmentationBudgetCheck admission = new LlmClient.SegmentationBudgetCheck(
                ModelSlot.NLP_SMALL.slotName(), 128000, 89600, 10, 10, 20, true
        );

        when(llmClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(llmClient.detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble())).thenReturn(
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
                .isInstanceOf(SceneDetectionException.class)
                .hasMessageContaining("Scene coordinate localization dropped scenes");

        verify(llmClient, times(1)).detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble());
    }

    @Test
    @DisplayName("Should fail when a segment produces no localizable scenes")
    void shouldFailWhenSegmentProducesNoLocalizableScenes() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Paragraph one sentence one.\n\nParagraph two sentence two.".repeat(40);

        LlmClient.SegmentationBudgetCheck admission = new LlmClient.SegmentationBudgetCheck(
                ModelSlot.NLP_BIG.slotName(), 400, 100, 20, 180, 200, false
        );

        when(llmClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(llmClient.detectChapterSegmentation(eq(jobId), any(String.class), anyDouble())).thenReturn(
                "<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>"
        );
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(0, "a", "ctx", "", "", "", "")));
        when(sceneProcessingService.localizeSceneCoordinates(any(String.class), any())).thenReturn(List.of());

        assertThatThrownBy(() -> sceneDetectionService.detectScenesInText(jobId, chapterId, chapterText))
                .isInstanceOf(SceneDetectionException.class)
                .hasMessageContaining("Scene coordinate localization returned empty results");

        verify(llmClient, times(1)).detectChapterSegmentation(eq(jobId), any(String.class), anyDouble());
    }

    @Test
    @DisplayName("Should throw SceneLocalizationException directly")
    void shouldThrowSceneLocalizationExceptionDirectly() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        String chapterText = "Short text.";

        LlmClient.SegmentationBudgetCheck admission = new LlmClient.SegmentationBudgetCheck(
                ModelSlot.NLP_SMALL.slotName(), 128000, 89600, 10, 10, 20, true
        );

        when(llmClient.evaluateSegmentationBudget(chapterText)).thenReturn(admission);
        when(llmClient.detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble())).thenReturn(
                "<scenes><scene><index>0</index><start_anchor>a</start_anchor><context_summary>x</context_summary></scene></scenes>"
        );
        when(sceneProcessingService.parseSceneDetectionXml(any(String.class), anyInt()))
                .thenReturn(List.of(new SceneDetectionResult(4, "anchor", "ctx", "", "", "", "")));

        SceneLocalizationException failure = new SceneLocalizationException(
                IngestionFailure.builder(
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

        verify(llmClient, times(1)).detectChapterSegmentation(eq(jobId), eq(chapterText), anyDouble());
    }

}
