package com.lorevault.api.ingestion;

import com.lorevault.api.support.BookLocationResolutionResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BookLocationReductionHandler {

    private static final Logger log = LoggerFactory.getLogger(BookLocationReductionHandler.class);

    private final BookLocationReductionService bookLocationReductionService;
    private final ApplicationEventPublisher eventPublisher;

    public BookLocationReductionHandler(
            BookLocationReductionService bookLocationReductionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookLocationReductionService = bookLocationReductionService;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleChapterLocationsResolved(ChapterLocationsResolvedEvent event) {
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        UUID jobId = (UUID) eventBean.getPropertyValue("jobId");
        UUID chapterId = (UUID) eventBean.getPropertyValue("chapterId");
        UUID bookId = (UUID) eventBean.getPropertyValue("bookId");

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
