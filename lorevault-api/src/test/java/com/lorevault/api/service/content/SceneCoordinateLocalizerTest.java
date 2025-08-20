package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("service")
@DisplayName("SceneCoordinateLocalizer")
class SceneCoordinateLocalizerTest {

    private final SceneCoordinateLocalizer localizer = new SceneCoordinateLocalizer();

    @Test
    @DisplayName("should localize coordinates for multiple scenes in order")
    void shouldLocalizeCoordinatesForMultipleScenesInOrder() {
        String chapterText = "Chapter start. Scene one begins here with this anchor text. " +
                           "Some content in between the scenes. " +
                           "Scene two starts at this point in the text. " +
                           "Final content in scene two until the end.";
        
        List<SceneDetectionResult> aiResults = List.of(
            new SceneDetectionResult(1, "Scene one begins here", "First scene description", 
                                   "Opening", "R:temporal.start", "Explicit", "Chapter start"),
            new SceneDetectionResult(2, "Scene two starts at this point", "Second scene description",
                                   "Transition", "R:temporal.continues", "Heuristic", "Midway")
        );

        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates(chapterText, aiResults);

        assertThat(coordinated).hasSize(2);
        
        // Scene 1: should start where anchor is found and end where scene 2 begins
        SceneWithCoordinates scene1 = coordinated.get(0);
        assertThat(scene1.sceneIndex()).isEqualTo(1);
        assertThat(scene1.startCharacterOffset()).isEqualTo(chapterText.indexOf("Scene one begins here"));
        assertThat(scene1.endCharacterOffset()).isEqualTo(chapterText.indexOf("Scene two starts at this point"));
        assertThat(scene1.contextSummary()).isEqualTo("First scene description");
        
        // Scene 2: should start where its anchor is found and end at chapter end
        SceneWithCoordinates scene2 = coordinated.get(1);
        assertThat(scene2.sceneIndex()).isEqualTo(2);
        assertThat(scene2.startCharacterOffset()).isEqualTo(chapterText.indexOf("Scene two starts at this point"));
        assertThat(scene2.endCharacterOffset()).isEqualTo((long) chapterText.length());
        assertThat(scene2.contextSummary()).isEqualTo("Second scene description");
    }

    @Test
    @DisplayName("should handle single scene spanning entire chapter")
    void shouldHandleSingleSceneSpanningEntireChapter() {
        String chapterText = "This is a short chapter with only one scene. The anchor text is here.";
        
        List<SceneDetectionResult> aiResults = List.of(
            new SceneDetectionResult(1, "The anchor text is here", "Only scene in chapter",
                                   "Single scene", "R:temporal.start", "Explicit", "Entire chapter")
        );

        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates(chapterText, aiResults);

        assertThat(coordinated).hasSize(1);
        SceneWithCoordinates scene = coordinated.get(0);
        assertThat(scene.sceneIndex()).isEqualTo(1);
        assertThat(scene.startCharacterOffset()).isEqualTo(chapterText.indexOf("The anchor text is here"));
        assertThat(scene.endCharacterOffset()).isEqualTo((long) chapterText.length());
    }

    @Test
    @DisplayName("should skip scenes with unfindable anchors")
    void shouldSkipScenesWithUnfindableAnchors() {
        String chapterText = "This text does not contain the expected anchors.";
        
        List<SceneDetectionResult> aiResults = List.of(
            new SceneDetectionResult(1, "Nonexistent anchor text", "First scene",
                                   "Missing", "R:temporal.start", "Explicit", "Not found"),
            new SceneDetectionResult(2, "This text does not contain", "Second scene",
                                   "Partial match", "R:temporal.continues", "Explicit", "Found")
        );

        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates(chapterText, aiResults);

        // Only scene 2 should be found (partial text match)
        assertThat(coordinated).hasSize(1);
        SceneWithCoordinates scene = coordinated.get(0);
        assertThat(scene.sceneIndex()).isEqualTo(2);
        assertThat(scene.startCharacterOffset()).isEqualTo(0L); // Start of matching text
    }

    @Test
    @DisplayName("should handle scenes in non-sequential order")
    void shouldHandleScenesInNonSequentialOrder() {
        String chapterText = "First anchor appears here early. " +
                           "Middle content between scenes. " +
                           "Second anchor appears here later. " +
                           "More content after second scene.";
        
        // Submit scenes in reverse order to test sorting
        List<SceneDetectionResult> aiResults = List.of(
            new SceneDetectionResult(3, "Second anchor appears here", "Third scene",
                                   "Later scene", "R:temporal.continues", "Explicit", "Late"),
            new SceneDetectionResult(1, "First anchor appears here", "First scene",
                                   "Opening", "R:temporal.start", "Explicit", "Early")
        );

        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates(chapterText, aiResults);

        assertThat(coordinated).hasSize(2);
        
        // Should be sorted by scene index, not input order
        SceneWithCoordinates scene1 = coordinated.get(0);
        assertThat(scene1.sceneIndex()).isEqualTo(1);
        assertThat(scene1.startCharacterOffset()).isEqualTo(chapterText.indexOf("First anchor appears here"));
        
        SceneWithCoordinates scene3 = coordinated.get(1);
        assertThat(scene3.sceneIndex()).isEqualTo(3);
        assertThat(scene3.startCharacterOffset()).isEqualTo(chapterText.indexOf("Second anchor appears here"));
    }

    @Test
    @DisplayName("should handle empty input gracefully")
    void shouldHandleEmptyInputGracefully() {
        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates("Some text", List.of());
        assertThat(coordinated).isEmpty();

        coordinated = localizer.localizeCoordinates("", List.of(
            new SceneDetectionResult(1, "anchor", "summary", "reason", "R:temporal.start", "Explicit", "marker")
        ));
        assertThat(coordinated).isEmpty();
    }

    @Test
    @DisplayName("should require exact case match for anchors")
    void shouldRequireExactCaseMatchForAnchors() {
        String chapterText = "The Captain walked onto the bridge.";
        
        List<SceneDetectionResult> aiResults = List.of(
            new SceneDetectionResult(1, "Captain walked", "Scene with captain",
                                   "Case test", "R:temporal.start", "Explicit", "Bridge scene")
        );

        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates(chapterText, aiResults);

        assertThat(coordinated).hasSize(1);
        SceneWithCoordinates scene = coordinated.get(0);
        assertThat(scene.startCharacterOffset()).isEqualTo(chapterText.indexOf("Captain walked"));
    }

    @Test
    @DisplayName("should require exact whitespace match in anchors")
    void shouldRequireExactWhitespaceMatchInAnchors() {
        String chapterText = "The ship sailed across the ocean.";
        
        List<SceneDetectionResult> aiResults = List.of(
            new SceneDetectionResult(1, "ship sailed across", "Sailing scene",
                                   "Whitespace test", "R:temporal.continues", "Heuristic", "Ocean")
        );

        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates(chapterText, aiResults);

        assertThat(coordinated).hasSize(1);
        SceneWithCoordinates scene = coordinated.get(0);
        assertThat(scene.startCharacterOffset()).isEqualTo(chapterText.indexOf("ship sailed across"));
    }
}
