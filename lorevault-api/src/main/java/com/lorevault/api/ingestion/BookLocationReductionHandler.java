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

        log.info("[BOOK_LOCATION_REDUCTION] Starting automatic reduction for book={} after chapter={}", bookId, chapterId);

        BookLocationResolutionResponse response = bookLocationReductionService.resolveBook(bookId);
        eventPublisher.publishEvent(new BookLocationsReducedEvent(
                this,
                jobId,
                chapterId,
                bookId,
                response.isProcessed(),
                response.getChapterLocationCount(),
                response.getBookLocationCount()
        ));
    }
}
