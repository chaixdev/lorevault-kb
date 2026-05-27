package com.lorevault.api.ingestion.resolution.consolidation;

import com.lorevault.api.content.chapter.ChapterGraphRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared guard that checks whether a chapter exists before resolution.
 *
 * <p>Replaces 4 identical {@code chapterExists(UUID)} copies across
 * Individual, Location, Object, and Collective chapter resolution services.
 */
@Service
public class ChapterEntityGuardService {

    private final ChapterGraphRepository chapterGraphRepository;

    public ChapterEntityGuardService(ChapterGraphRepository chapterGraphRepository) {
        this.chapterGraphRepository = chapterGraphRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }
}
