package com.lorevault.api.ingestion;

import com.lorevault.api.support.ChapterLocationResolutionResponse;
import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChapterLocationResolutionHandler {

    private final ChapterLocationResolutionService chapterLocationResolutionService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID bookId = event.getBookId();

        log.info("[CHAPTER_LOCATION_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            ChapterLocationResolutionResponse response = chapterLocationResolutionService.resolveChapter(chapterId);

            if (response.isProcessed()) {
                log.info(
                        "[CHAPTER_LOCATION_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterLocationCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.getMentionCount(),
                        response.getChapterLocationCount()
                );
            } else {
                log.warn(
                        "[CHAPTER_LOCATION_RESOLUTION] Skipped: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.getMentionCount(),
                        response.getChapterLocationCount(),
                        response.getMessage()
                );
            }

            eventPublisher.publishEvent(new ChapterLocationsResolvedEvent(
                    this,
                    jobId,
                    chapterId,
                    bookId,
                    response.isProcessed(),
                    response.getMentionCount(),
                    response.getChapterLocationCount()
            ));
        } catch (Exception e) {
            log.error("[CHAPTER_LOCATION_RESOLUTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
