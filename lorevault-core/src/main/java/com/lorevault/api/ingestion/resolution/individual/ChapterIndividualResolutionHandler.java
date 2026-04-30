package com.lorevault.api.ingestion.resolution.individual;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChapterIndividualResolutionHandler {

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;
    private final ApplicationEventPublisher eventPublisher;

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID bookId = event.getBookId();

        log.info("[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            ChapterIndividualResolutionResult response = chapterIndividualResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterIndividualCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated()
                );
            } else {
                log.warn(
                        "[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Skipped: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterIndividualCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated(),
                        response.message()
                );
            }

            eventPublisher.publishEvent(new ChapterIndividualsResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    response.success(),
                    response.rawIndividualsProcessed(),
                    response.chapterIndividualsCreated()
            ));
        } catch (Exception e) {
            log.error("[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
