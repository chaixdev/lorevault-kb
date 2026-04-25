package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
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
public class ChapterEventResolutionHandler {

    private final ChapterEventResolutionService chapterEventResolutionService;
    private final ApplicationEventPublisher eventPublisher;

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID bookId = event.getBookId();

        log.info("[CHAPTER_EVENT_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            ChapterEventResolutionResult result = chapterEventResolutionService.resolveChapter(chapterId);

            if (result.success()) {
                log.info(
                        "[CHAPTER_EVENT_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterEventCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        result.rawMentionsProcessed(),
                        result.chapterEventsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_EVENT_RESOLUTION] Skipped: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterEventCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        result.rawMentionsProcessed(),
                        result.chapterEventsCreated(),
                        result.message()
                );
            }

            eventPublisher.publishEvent(new ChapterEventsResolvedEvent(
                    this,
                    jobId,
                    chapterId,
                    bookId,
                    result.success(),
                    result.rawMentionsProcessed(),
                    result.chapterEventsCreated()
            ));
        } catch (Exception e) {
            log.error("[CHAPTER_EVENT_RESOLUTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
