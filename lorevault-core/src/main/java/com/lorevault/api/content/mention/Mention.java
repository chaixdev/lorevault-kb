package com.lorevault.api.content.mention;

import java.util.UUID;

/**
 * Narrow capability contract for raw extracted mention nodes, captures the common naming, scope, and lifecycle shape
 * without pulling type-specific extraction details into a shared abstraction.
 */
public interface Mention {
    UUID id();

    String displayName();

    String normalizedName();

    UUID sceneId();

    UUID chapterId();

    UUID bookId();

    String resolutionStatus();
}
