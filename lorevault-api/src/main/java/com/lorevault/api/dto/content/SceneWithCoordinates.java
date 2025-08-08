package com.lorevault.api.dto.content;

/**
 * Data transfer object representing a scene with calculated character coordinates.
 * Result of the coordinate localization phase where AI-identified anchors 
 * are converted to precise character offsets within the chapter text.
 * 
 * @param sceneIndex The 1-based index of the scene within the chapter
 * @param startCharacterOffset Character offset where the scene begins (inclusive)
 * @param endCharacterOffset Character offset where the scene ends (exclusive)
 * @param contextSummary Brief description of what happens in this scene
 */
public record SceneWithCoordinates(
    int sceneIndex,
    long startCharacterOffset,
    long endCharacterOffset,
    String contextSummary
) {}
