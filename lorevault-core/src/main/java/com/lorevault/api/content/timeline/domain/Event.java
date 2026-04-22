package com.lorevault.api.content.timeline.domain;

import java.util.UUID;

/**
 * Event is the semantic modality for scenes/timeline elements.
 * Minimal contract for v0.9.0 to support temporal edges.
 */
public interface Event {
    UUID getEventId();
    UUID getChapterId();
    Integer getSceneIndex();
    Long getStartOffset();
    Long getEndOffset();
}
