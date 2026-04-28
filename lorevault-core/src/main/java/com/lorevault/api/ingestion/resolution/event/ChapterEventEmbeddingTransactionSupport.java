package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.content.association.ChapterEvent;
import com.lorevault.api.content.association.ChapterEventGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Transactional DB operations for the ChapterEvent embedding pipeline.
 *
 * <p>Extracted into a separate bean so that the Spring proxy is honoured and external
 * embedding API calls can happen outside any DB transaction boundary — the same
 * pattern used by {@code EmbeddingTransactionSupport} for chunks.
 *
 * <ul>
 *   <li>{@link #loadChapterEvents(UUID)} — read-only, called before the API call.</li>
 *   <li>{@link #saveChapterEvents(List)} — read-write, called after the API call.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterEventEmbeddingTransactionSupport {

    private final ChapterEventGraphRepository chapterEventRepo;

    /**
     * Load all ChapterEvent nodes for the chapter.
     * Called before any external embedding API call — no write lock held.
     */
    @Transactional(readOnly = true)
    List<ChapterEvent> loadChapterEvents(UUID chapterId) {
        List<ChapterEvent> events = chapterEventRepo.findByChapterId(chapterId);
        log.debug("[EventEmbeddings] Loaded {} ChapterEvents chapter={}", events.size(), chapterId);
        return events;
    }

    /**
     * Persist updated ChapterEvent nodes after the external API call completes.
     * No external I/O inside this transaction boundary.
     */
    @Transactional
    void saveChapterEvents(List<ChapterEvent> events) {
        chapterEventRepo.saveAll(events);
    }
}
