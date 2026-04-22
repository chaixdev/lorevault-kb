package com.lorevault.api.ingestion.application;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.ingestion.application.BookLocationResolutionResult;
import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
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
public class BookLocationReductionHandler {

    private final BookLocationReductionService bookLocationReductionService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void handleChapterLocationsResolved(ChapterLocationsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        log.info("[BOOK_LOCATION_REDUCTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            BookLocationResolutionResult response = bookLocationReductionService.resolveBook(bookId);

            if (response.success()) {
                log.info(
                        "[BOOK_LOCATION_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.chapterLocationsProcessed(),
                        response.bookLocationsCreated()
                );
            } else {
                log.warn(
                        "[BOOK_LOCATION_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.chapterLocationsProcessed(),
                        response.bookLocationsCreated(),
                        response.message()
                );
            }

            eventPublisher.publishEvent(new BookLocationsReducedEvent(
                    this,
                    jobId,
                    chapterId,
                    bookId,
                    response.success(),
                    response.chapterLocationsProcessed(),
                    response.bookLocationsCreated()
            ));
        } catch (Exception e) {
            log.error("[BOOK_LOCATION_REDUCTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
