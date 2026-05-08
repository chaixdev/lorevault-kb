package com.lorevault.api.ingestion.resolution.location;

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

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID bookId = event.getBookId();

        log.info("[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            ChapterLocationResolutionResult response = chapterLocationResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterLocationCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated()
                );
            } else {
                log.warn(
                        "[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Skipped: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated(),
                        response.message()
                );
            }

            eventPublisher.publishEvent(new ChapterLocationsResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    response.success(),
                    response.rawLocationsProcessed(),
                    response.chapterLocationsCreated()
            ));
        } catch (Exception e) {
            log.error("[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
