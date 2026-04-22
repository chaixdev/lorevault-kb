package com.lorevault.api.ingestion.application;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.lorevault.api.ingestion.application.ChapterIndividualResolutionResult;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChapterIndividualResolutionHandler {

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID bookId = event.getBookId();

        log.info("[CHAPTER_INDIVIDUAL_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            ChapterIndividualResolutionResult response = chapterIndividualResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_INDIVIDUAL_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterIndividualCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_INDIVIDUAL_RESOLUTION] Skipped: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterIndividualCount={}, reason={}",
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
                    chapterId,
                    bookId,
                    response.success(),
                    response.rawIndividualsProcessed(),
                    response.chapterIndividualsCreated()
            ));
        } catch (Exception e) {
            log.error("[CHAPTER_INDIVIDUAL_RESOLUTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
