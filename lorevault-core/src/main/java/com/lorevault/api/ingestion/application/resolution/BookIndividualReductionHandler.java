package com.lorevault.api.ingestion.application.resolution;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.ingestion.application.result.BookIndividualResolutionResult;
import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class BookIndividualReductionHandler {

    private final BookIndividualReductionService bookIndividualReductionService;
    private final ApplicationEventPublisher eventPublisher;

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleChapterIndividualsResolved(ChapterIndividualsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        log.info("[BOOK_INDIVIDUAL_REDUCTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            BookIndividualResolutionResult response = bookIndividualReductionService.resolveBook(bookId);

            if (response.success()) {
                log.info(
                        "[BOOK_INDIVIDUAL_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.chapterIndividualsProcessed(),
                        response.bookIndividualsCreated()
                );
            } else {
                log.warn(
                        "[BOOK_INDIVIDUAL_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.chapterIndividualsProcessed(),
                        response.bookIndividualsCreated(),
                        response.message()
                );
            }

            eventPublisher.publishEvent(new BookIndividualsReducedEvent(
                    this,
                    jobId,
                    chapterId,
                    bookId,
                    response.success(),
                    response.chapterIndividualsProcessed(),
                    response.bookIndividualsCreated()
            ));
        } catch (Exception e) {
            log.error("[BOOK_INDIVIDUAL_REDUCTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
