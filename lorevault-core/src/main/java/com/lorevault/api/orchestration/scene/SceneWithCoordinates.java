package com.lorevault.api.orchestration.scene;

/**
 * Data transfer object representing a scene with calculated character coordinates.
 * Result of the coordinate localization phase where AI-identified anchors
 * are converted to precise character offsets within the chapter text.
 *
 * @param sceneIndex The 0-based index of the scene within the chapter
 * @param startCharacterOffset Character offset where the scene begins (inclusive)
 * @param endCharacterOffset Character offset where the scene ends (exclusive)
 * @param contextSummary Brief description of what happens in this scene
 * @param potentialSplitSceneStart Whether this scene may be a split fragment start
 * @param potentialSplitSceneEnd Whether this scene may be a split fragment end
 */
public record SceneWithCoordinates(
    int sceneIndex,
    long startCharacterOffset,
    long endCharacterOffset,
    String contextSummary,
    boolean potentialSplitSceneStart,
    boolean potentialSplitSceneEnd
) {
    public SceneWithCoordinates(int sceneIndex, long startCharacterOffset, long endCharacterOffset, String contextSummary) {
        this(sceneIndex, startCharacterOffset, endCharacterOffset, contextSummary, false, false);
    }
}
