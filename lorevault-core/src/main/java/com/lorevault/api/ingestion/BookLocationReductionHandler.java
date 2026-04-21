package com.lorevault.api.ingestion;

import com.lorevault.api.support.BookLocationResolutionResponse;
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
            BookLocationResolutionResponse response = bookLocationReductionService.resolveBook(bookId);

            if (response.isProcessed()) {
                log.info(
                        "[BOOK_LOCATION_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.getChapterLocationCount(),
                        response.getBookLocationCount()
                );
            } else {
                log.warn(
                        "[BOOK_LOCATION_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.getChapterLocationCount(),
                        response.getBookLocationCount(),
                        response.getMessage()
                );
            }

            eventPublisher.publishEvent(new BookLocationsReducedEvent(
                    this,
                    jobId,
                    chapterId,
                    bookId,
                    response.isProcessed(),
                    response.getChapterLocationCount(),
                    response.getBookLocationCount()
            ));
        } catch (Exception e) {
            log.error("[BOOK_LOCATION_REDUCTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
