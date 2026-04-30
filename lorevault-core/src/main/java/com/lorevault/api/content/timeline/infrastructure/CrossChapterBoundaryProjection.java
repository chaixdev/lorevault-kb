package com.lorevault.api.content.timeline.infrastructure;

import java.util.UUID;

public interface CrossChapterBoundaryProjection {
    UUID getPreviousChapterId();

    UUID getNextChapterId();

    UUID getPreviousSceneId();

    UUID getNextSceneId();
}
