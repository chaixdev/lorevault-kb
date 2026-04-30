package com.lorevault.api.search.model;

/**
 * Controls what happens when a series appears in search results but is absent
 * from the caller's {@link SpoilerVisibility} progress list.
 *
 * <ul>
 *   <li>{@code HIDE} — exclude all chunks from unconfigured series (safe default).</li>
 *   <li>{@code SHOW} — include all chunks from unconfigured series without restriction.</li>
 * </ul>
 *
 * Callers that supply a {@code SpoilerVisibility} object without specifying a
 * policy receive {@code HIDE} behaviour, preventing accidental spoilers when new
 * series are added to a universe the reader has not yet registered progress for.
 */
public enum UnconfiguredSeriesPolicy {
    HIDE,
    SHOW
}
