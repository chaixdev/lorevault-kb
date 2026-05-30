package com.lorevault.api.orchestration.consolidation;

import java.util.UUID;

import com.lorevault.api.library.chapter.ChapterGraphRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared guard that checks whether a chapter exists before consolidation proceeds.
 *
 * <p>Replaces duplicated {@code chapterExists()} methods across the four
 * chapter-level consolidation services (Individual, Location, Object, Collective).
 */
@Service
@RequiredArgsConstructor
public class ChapterEntityGuardService {

    private final ChapterGraphRepository chapterGraphRepository;

    /**
     * Check whether a chapter exists.
     *
     * @param chapterId  the chapter ID (may be null)
     * @return true if the chapter exists, false if null or not found
     */
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }
}