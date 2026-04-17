package com.lorevault.api.ingestion;

import com.lorevault.api.support.ChapterLocationResolutionResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ChapterLocationResolutionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChapterLocationResolutionHandler.class);

    private final ChapterLocationResolutionService chapterLocationResolutionService;
    private final ApplicationEventPublisher eventPublisher;

    public ChapterLocationResolutionHandler(
            ChapterLocationResolutionService chapterLocationResolutionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterLocationResolutionService = chapterLocationResolutionService;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        UUID chapterId = (UUID) eventBean.getPropertyValue("chapterId");
        UUID jobId = (UUID) eventBean.getPropertyValue("jobId");
        UUID bookId = (UUID) eventBean.getPropertyValue("bookId");

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
