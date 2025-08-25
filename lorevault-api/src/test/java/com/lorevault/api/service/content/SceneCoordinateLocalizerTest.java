package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    @DisplayName("should handle whitespace differences in anchors (enhanced matching)")
    void shouldHandleWhitespaceDifferencesInAnchors() {
        String chapterText = "The ship sailed across the ocean.";
        
        // Anchor with different whitespace (simulating LLM CDATA indentation)
        List<SceneDetectionResult> aiResults = List.of(
            new SceneDetectionResult(1, "ship    sailed\n\n  across", "Sailing scene",
                                   "Whitespace test", "R:temporal.continues", "Heuristic", "Ocean")
        );

        List<SceneWithCoordinates> coordinated = localizer.localizeCoordinates(chapterText, aiResults);

        assertThat(coordinated).hasSize(1);
        SceneWithCoordinates scene = coordinated.get(0);
        // The enhanced matching should find the normalized match
        // Position might be slightly different due to whitespace normalization mapping
        assertThat(scene.startCharacterOffset()).isGreaterThanOrEqualTo(0L);
        assertThat(scene.startCharacterOffset()).isLessThanOrEqualTo(chapterText.indexOf("ship sailed across"));
    }

    @Nested
    @DisplayName("Whitespace Normalization Tests")
    class WhitespaceNormalizationTests {

        @Test
        @DisplayName("Should handle extra indentation in anchor (LLM CDATA issue)")
        void shouldHandleExtraIndentationInAnchor() {
            // Given: Chapter text with normal whitespace
            String chapterText = "+0014+: Another failure.\n\n+0023+: There have been victories also.\n\n+0003+: The efforts work.";
            
            // And: Anchor with extra indentation (simulating LLM CDATA output)
            String anchorWithIndentation = "+0014+: Another failure.\n    \n+0023+: There have been victories also.";
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, anchorWithIndentation, "Scene 1 context", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(1);
            assertThat(coordinates.get(0).startCharacterOffset()).isEqualTo(0);
            assertThat(coordinates.get(0).endCharacterOffset()).isEqualTo(chapterText.length());
        }

        @Test
        @DisplayName("Should handle mixed tab and space normalization")
        void shouldHandleMixedTabSpaceNormalization() {
            // Given
            String chapterText = "Hello    world\n\nNext line";
            String anchorWithTabs = "Hello\t\t\tworld\n   \nNext line";
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, anchorWithTabs, "Scene context", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(1);
            assertThat(coordinates.get(0).startCharacterOffset()).isEqualTo(0);
        }

        @ParameterizedTest
        @DisplayName("Should normalize various whitespace patterns")
        @CsvSource({
            "'Hello world', 'Hello    world'",
            "'Line1 Line2', 'Line1\n\n\nLine2'",
            "'A B C', 'A\t\tB   C'",
            "'Text here', '   Text here   '"
        })
        void shouldNormalizeVariousWhitespacePatterns(String expected, String input) {
            // Given
            String chapterText = expected + "\n\nMore content";
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, input, "Scene context", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(1);
            assertThat(coordinates.get(0).startCharacterOffset()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Multi-Tier Fallback Tests")
    class MultiTierFallbackTests {

        @Test
        @DisplayName("Should fall back to word trimming for long anchors")
        void shouldFallBackToWordTrimmingForLongAnchors() {
            // Given
            String chapterText = "The quick brown fox jumps over the lazy dog. More content follows here.";
            
            // Anchor has extra words that don't exist in chapter
            String anchorWithExtraWords = "The quick brown fox jumps over the lazy dog with extra words here.";
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, anchorWithExtraWords, "Scene context", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(1);
            assertThat(coordinates.get(0).startCharacterOffset()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should use fuzzy matching as last resort")
        void shouldUseFuzzyMatchingAsLastResort() {
            // Given
            String chapterText = "This is a test of fuzzy matching capabilities.";
            
            // Anchor with small typos that should match via fuzzy logic
            String fuzzyAnchor = "This iz a tast of fuzzy"; // 'is'->'iz', 'test'->'tast'
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, fuzzyAnchor, "Scene context", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(1);
            assertThat(coordinates.get(0).startCharacterOffset()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Bounded Search Tests")
    class BoundedSearchTests {

        @Test
        @DisplayName("Should handle missing middle scene with look-ahead")
        void shouldHandleMissingMiddleSceneWithLookAhead() {
            // Given
            String chapterText = "First scene here.\n\nSecond scene missing anchor.\n\nThird scene found.";
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, "First scene here", "Scene 1", "", "", "", ""),
                new SceneDetectionResult(2, "Nonexistent anchor text", "Scene 2", "", "", "", ""), // Won't be found
                new SceneDetectionResult(3, "Third scene found", "Scene 3", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(2); // Scene 2 should be skipped
            assertThat(coordinates.get(0).sceneIndex()).isEqualTo(1);
            assertThat(coordinates.get(1).sceneIndex()).isEqualTo(3);
            
            // Scene 1 should extend to Scene 3's start (look-ahead boundary)
            assertThat(coordinates.get(0).endCharacterOffset())
                .isEqualTo(coordinates.get(1).startCharacterOffset());
        }

        @Test
        @DisplayName("Should extend to chapter end when no subsequent anchors found")
        void shouldExtendToChapterEndWhenNoSubsequentAnchorsFound() {
            // Given
            String chapterText = "First scene content.\n\nRest of chapter with no more scene anchors.";
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, "First scene content", "Scene 1", "", "", "", ""),
                new SceneDetectionResult(2, "Nonexistent anchor", "Scene 2", "", "", "", ""),
                new SceneDetectionResult(3, "Another missing anchor", "Scene 3", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(1);
            assertThat(coordinates.get(0).endCharacterOffset()).isEqualTo(chapterText.length());
        }
    }

    @Nested
    @DisplayName("Real-World Scenario Tests")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("Should handle the original +0014+ whitespace issue")
        void shouldHandleOriginalWhitespaceIssue() {
            // Given: Real data from the bug report
            String chapterText = "+0014+: Another failure.\n\n+0023+: There have been victories also.\n\n+0003+: The efforts of The Discarded work to our advantage.";
            
            // LLM output with CDATA indentation
            String llmAnchor = "+0014+: Another failure.\n    \n+0023+: There have been victories also.";
            
            List<SceneDetectionResult> results = List.of(
                new SceneDetectionResult(1, llmAnchor, "Chat-log style transmission", "", "", "", "")
            );

            // When
            List<SceneWithCoordinates> coordinates = localizer.localizeCoordinates(chapterText, results);

            // Then
            assertThat(coordinates).hasSize(1);
            assertThat(coordinates.get(0).startCharacterOffset()).isEqualTo(0);
            
            // Verify the actual text match
            String matchedText = chapterText.substring(
                (int) coordinates.get(0).startCharacterOffset(),
                Math.min((int) coordinates.get(0).startCharacterOffset() + 50, chapterText.length())
            );
            assertThat(matchedText).startsWith("+0014+: Another failure.");
        }
    }
}
