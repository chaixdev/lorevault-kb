package com.lorevault.api.content.timeline.domain;

import java.util.UUID;

public interface CrossChapterBoundaryProjection {
    UUID getPreviousChapterId();

    UUID getNextChapterId();

    UUID getPreviousSceneId();

    UUID getNextSceneId();
}
