package com.lorevault.api.ingestion.application.scene;

import com.lorevault.api.ingestion.scene.SceneLocalizationException;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.scene.SceneGraphRepository;
import com.lorevault.api.ingestion.scene.SceneDetectionResult;
import com.lorevault.api.ingestion.scene.SceneProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("SceneProcessingService XML parsing tests")
class SceneProcessingServiceTest {

    @Mock
    private ChapterGraphRepository chapterRepo;

    @Mock
    private SceneGraphRepository sceneRepo;

    @InjectMocks
    private SceneProcessingService sceneProcessingService;

    @Test
    @DisplayName("Should return empty list for null or blank XML response")
    void parseSceneDetectionXml_nullOrBlank_returnsEmpty() {
        assertThat(sceneProcessingService.parseSceneDetectionXml(null, 100)).isEmpty();
        assertThat(sceneProcessingService.parseSceneDetectionXml("   ", 100)).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list for malformed XML response")
    void parseSceneDetectionXml_malformedXml_returnsEmpty() {
        List<SceneDetectionResult> results = sceneProcessingService.parseSceneDetectionXml(
                "<scenes><scene><index>0</index><start_anchor>alpha</start_anchor>",
                100
        );

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should parse valid XML scene response")
    void parseSceneDetectionXml_validXml_returnsParsedScenes() {
        String xml = """
                <scenes>
                  <scene>
                    <index>0</index>
                    <start_anchor>The dawn broke</start_anchor>
                    <context_summary>Opening scene</context_summary>
                    <break_reason>Initial setup</break_reason>
                    <chronology>BEFORE</chronology>
                    <chronology_certainty>HIGH</chronology_certainty>
                    <chronology_marker>at sunrise</chronology_marker>
                  </scene>
                </scenes>
                """;

        List<SceneDetectionResult> results = sceneProcessingService.parseSceneDetectionXml(xml, 1000);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).sceneIndex()).isEqualTo(0);
        assertThat(results.get(0).startAnchor()).isEqualTo("The dawn broke");
        assertThat(results.get(0).contextSummary()).isEqualTo("Opening scene");
    }

    @Test
    @DisplayName("Should throw typed localization failure when scene anchor is missing")
    void localizeSceneCoordinates_missingAnchor_throwsSceneLocalizationException() {
        List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(2, "missing anchor", "ctx", "", "", "", "")
        );

        assertThatThrownBy(() -> sceneProcessingService.localizeSceneCoordinates("Existing chapter text", results))
                .isInstanceOf(SceneLocalizationException.class)
                .hasMessageContaining("Failed to localize scene 2 because start anchor 'missing anchor' was not found");
    }
}
