package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.content.association.ChapterEvent;
import com.lorevault.api.content.association.ChapterEventGraphRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class ChapterEventEmbeddingTransactionSupport {

    private static final Logger log = LoggerFactory.getLogger(ChapterEventEmbeddingTransactionSupport.class);

    private final ChapterEventGraphRepository chapterEventRepo;

    public ChapterEventEmbeddingTransactionSupport(ChapterEventGraphRepository chapterEventRepo) {
        this.chapterEventRepo = chapterEventRepo;
    }

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

    @Transactional(readOnly = true)
    List<ChapterEvent> loadChapterEventsByIds(List<UUID> chapterEventIds) {
        if (chapterEventIds == null || chapterEventIds.isEmpty()) {
            return List.of();
        }

        List<ChapterEvent> events = chapterEventRepo.findByIds(chapterEventIds);
        log.debug("[EventEmbeddings] Loaded {} ChapterEvents by ids", events.size());
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
