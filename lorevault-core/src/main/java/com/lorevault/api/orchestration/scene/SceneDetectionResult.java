package com.lorevault.api.orchestration.scene;

/**
 * Data transfer object representing the result of AI scene detection.
 * Maps to the XML structure returned by the AI model.
 *
 * @param sceneIndex The 0-based index of the scene within the chapter
 * @param startAnchor Text fragment marking the beginning of the scene
 * @param contextSummary Brief description of what happens in this scene
 * @param breakReason Why the AI determined this is a scene boundary
 */
public record SceneDetectionResult(
    int sceneIndex,
    String startAnchor,
    String contextSummary,
    String breakReason
) {}
