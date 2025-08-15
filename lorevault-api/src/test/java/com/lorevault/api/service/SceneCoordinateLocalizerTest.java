package com.lorevault.api.service;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.SceneCoordinateLocalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SceneCoordinateLocalizer focusing on coordinate localization logic.
 */
class SceneCoordinateLocalizerTest {

    private SceneCoordinateLocalizer coordinateLocalizer;
    private String sampleChapterText;
    
    @BeforeEach
    void setUp() {
        coordinateLocalizer = new SceneCoordinateLocalizer();
        
        // Sample chapter text with clear scene boundaries
        sampleChapterText = "The morning sun filtered through the curtains as Sarah woke up in her bedroom. "
            + "She stretched and yawned, looking at the clock on her nightstand. It was already 8 AM.\n\n"
            + "An hour later, Sarah walked into the bustling coffee shop downtown. The aroma of "
            + "freshly ground beans filled the air as she waited in line. The barista smiled "
            + "warmly as she approached the counter.\n\n"
            + "That evening, Sarah sat in her car outside the old library. The rain drummed "
            + "against the windshield as she gathered her courage. This was where it all began, "
            + "twenty years ago.";
    }
    
    @Test
    void localizeCoordinates_ShouldLocateAllAnchors() {
        // Create scene detection results with anchors
        List<SceneDetectionResult> sceneResults = List.of(
            new SceneDetectionResult(1, "The morning sun", "Initial scene", "First scene", 
                                   "R:temporal.meets", "Heuristic", "Chapter beginning"),
            new SceneDetectionResult(2, "An hour later", "Time jump", "Second scene", 
                                   "R:temporal.after", "Explicit", "An hour later"),
            new SceneDetectionResult(3, "That evening", "Evening scene", "Third scene", 
                                   "R:temporal.after", "Explicit", "That evening")
        );
        
        // Localize the coordinates
        List<SceneWithCoordinates> coordinates = coordinateLocalizer.localizeCoordinates(
            sampleChapterText, sceneResults);
        
        // Verify we got the correct number of scenes
        assertThat(coordinates).hasSize(3);
        
        // Verify first scene
        SceneWithCoordinates first = coordinates.get(0);
        assertThat(first.sceneIndex()).isEqualTo(1);
        assertThat(first.startCharacterOffset()).isEqualTo(0L); // "The morning sun" is at the start
        
        // Verify second scene
        SceneWithCoordinates second = coordinates.get(1);
        assertThat(second.sceneIndex()).isEqualTo(2);
        // With the new logic, second scene starts where first scene ends
        assertThat(second.startCharacterOffset()).isEqualTo(first.endCharacterOffset());
        
        // Verify third scene
        SceneWithCoordinates third = coordinates.get(2);
        assertThat(third.sceneIndex()).isEqualTo(3);
        // Third scene starts where second scene ends
        assertThat(third.startCharacterOffset()).isEqualTo(second.endCharacterOffset());
        
        // Verify scenes are contiguous and don't overlap
        assertThat(first.endCharacterOffset()).isEqualTo(second.startCharacterOffset());
        assertThat(second.endCharacterOffset()).isEqualTo(third.startCharacterOffset());
        // Last scene extends to end of chapter
        assertThat(third.endCharacterOffset()).isEqualTo(sampleChapterText.length());
    }
    
    @Test
    void localizeCoordinates_ShouldHandleAnchorsNotFound() {
        // Create scene detection results with some non-existent anchors
        List<SceneDetectionResult> sceneResults = List.of(
            new SceneDetectionResult(1, "The morning sun", "Initial scene", "First scene", 
                                   "R:temporal.meets", "Heuristic", "Chapter beginning"),
            new SceneDetectionResult(2, "This doesn't exist", "Invalid scene", "Nonexistent scene", 
                                   "R:temporal.after", "Heuristic", "Unknown")
        );
        
        // Localize the coordinates
        List<SceneWithCoordinates> coordinates = coordinateLocalizer.localizeCoordinates(
            sampleChapterText, sceneResults);
        
        // Should only include scenes where anchors were found
        assertThat(coordinates).hasSize(1);
        assertThat(coordinates.get(0).sceneIndex()).isEqualTo(1);
    }
}
