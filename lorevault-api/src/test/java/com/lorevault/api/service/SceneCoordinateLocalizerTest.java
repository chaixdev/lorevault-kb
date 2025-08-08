package com.lorevault.api.service;

import com.lorevault.api.dto.SceneDetectionResult;
import com.lorevault.api.dto.SceneWithCoordinates;
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
            new SceneDetectionResult(1, "The morning sun", "8 AM.", "Initial scene", "First scene"),
            new SceneDetectionResult(2, "An hour later", "the counter.", "Time jump", "Second scene"),
            new SceneDetectionResult(3, "That evening", "twenty years ago.", "Evening scene", "Third scene")
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
        assertThat(second.startCharacterOffset()).isGreaterThan(first.endCharacterOffset());
        
        // Verify third scene
        SceneWithCoordinates third = coordinates.get(2);
        assertThat(third.sceneIndex()).isEqualTo(3);
        assertThat(third.startCharacterOffset()).isGreaterThan(second.endCharacterOffset());
        
        // Verify scene boundaries don't overlap
        assertThat(first.endCharacterOffset()).isLessThanOrEqualTo(second.startCharacterOffset());
        assertThat(second.endCharacterOffset()).isLessThanOrEqualTo(third.startCharacterOffset());
    }
    
    @Test
    void localizeCoordinates_ShouldHandleAnchorsNotFound() {
        // Create scene detection results with some non-existent anchors
        List<SceneDetectionResult> sceneResults = List.of(
            new SceneDetectionResult(1, "The morning sun", "8 AM.", "Initial scene", "First scene"),
            new SceneDetectionResult(2, "This doesn't exist", "also not found", "Invalid scene", "Nonexistent scene")
        );
        
        // Localize the coordinates
        List<SceneWithCoordinates> coordinates = coordinateLocalizer.localizeCoordinates(
            sampleChapterText, sceneResults);
        
        // Should only include scenes where both anchors were found
        assertThat(coordinates).hasSize(1);
        assertThat(coordinates.get(0).sceneIndex()).isEqualTo(1);
    }
}
