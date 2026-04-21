package com.lorevault.api.ingestion;

import com.lorevault.api.support.BookIndividualResolutionResponse;
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

    @Async
    @EventListener
    public void handleChapterIndividualsResolved(ChapterIndividualsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        log.info("[BOOK_INDIVIDUAL_REDUCTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        try {
            BookIndividualResolutionResponse response = bookIndividualReductionService.resolveBook(bookId);

            if (response.isProcessed()) {
                log.info(
                        "[BOOK_INDIVIDUAL_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.getChapterIndividualCount(),
                        response.getBookIndividualCount()
                );
            } else {
                log.warn(
                        "[BOOK_INDIVIDUAL_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.getChapterIndividualCount(),
                        response.getBookIndividualCount(),
                        response.getMessage()
                );
            }

            eventPublisher.publishEvent(new BookIndividualsReducedEvent(
                    this,
                    jobId,
                    chapterId,
                    bookId,
                    response.isProcessed(),
                    response.getChapterIndividualCount(),
                    response.getBookIndividualCount()
            ));
        } catch (Exception e) {
            log.error("[BOOK_INDIVIDUAL_REDUCTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId, e);
            throw e;
        }
    }
}
