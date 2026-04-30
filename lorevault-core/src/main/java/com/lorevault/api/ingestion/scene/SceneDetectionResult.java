package com.lorevault.api.ingestion.scene;

/**
 * Data transfer object representing the result of AI scene detection.
 * Maps to the XML structure returned by the AI model.
 * 
 * @param sceneIndex The 0-based index of the scene within the chapter
 * @param startAnchor Text fragment marking the beginning of the scene
 * @param contextSummary Brief description of what happens in this scene
 * @param breakReason Why the AI determined this is a scene boundary
 * @param chronology Temporal relationship to the previous scene using Allen's Interval Algebra
 * @param chronologyCertainty Level of certainty about the temporal relationship
 * @param chronologyMarker Text evidence that supports the temporal relationship
 */
public record SceneDetectionResult(
    int sceneIndex,
    String startAnchor,
    String contextSummary,
    String breakReason,
    String chronology,
    String chronologyCertainty,
    String chronologyMarker
) {}
