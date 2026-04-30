package com.lorevault.api.ingestion.scene;

/**
 * Data transfer object representing a scene with calculated character coordinates.
 * Result of the coordinate localization phase where AI-identified anchors 
 * are converted to precise character offsets within the chapter text.
 * 
 * @param sceneIndex The 0-based index of the scene within the chapter
 * @param startCharacterOffset Character offset where the scene begins (inclusive)
 * @param endCharacterOffset Character offset where the scene ends (exclusive)
 * @param contextSummary Brief description of what happens in this scene
 * @param chronology Temporal relationship hint extracted during scene analysis
 * @param chronologyCertainty Certainty level for chronology hint
 * @param chronologyMarker Text marker supporting chronology hint
 * @param potentialSplitSceneStart Whether this scene may be a split fragment start
 * @param potentialSplitSceneEnd Whether this scene may be a split fragment end
 */
public record SceneWithCoordinates(
    int sceneIndex,
    long startCharacterOffset,
    long endCharacterOffset,
    String contextSummary,
    String chronology,
    String chronologyCertainty,
    String chronologyMarker,
    boolean potentialSplitSceneStart,
    boolean potentialSplitSceneEnd
) {
    public SceneWithCoordinates(int sceneIndex, long startCharacterOffset, long endCharacterOffset, String contextSummary) {
        this(sceneIndex, startCharacterOffset, endCharacterOffset, contextSummary, null, null, null, false, false);
    }

    public SceneWithCoordinates(int sceneIndex,
                                long startCharacterOffset,
                                long endCharacterOffset,
                                String contextSummary,
                                boolean potentialSplitSceneStart,
                                boolean potentialSplitSceneEnd) {
        this(sceneIndex, startCharacterOffset, endCharacterOffset, contextSummary, null, null, null,
                potentialSplitSceneStart, potentialSplitSceneEnd);
    }
}
