package com.lorevault.api.ingestion;

import com.lorevault.api.support.BookIndividualResolutionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BookIndividualReductionHandler {

    private static final Logger log = LoggerFactory.getLogger(BookIndividualReductionHandler.class);

    private final BookIndividualReductionService bookIndividualReductionService;
    private final ApplicationEventPublisher eventPublisher;

    public BookIndividualReductionHandler(
            BookIndividualReductionService bookIndividualReductionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookIndividualReductionService = bookIndividualReductionService;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleChapterIndividualsResolved(ChapterIndividualsResolvedEvent event) {
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        UUID jobId = (UUID) eventBean.getPropertyValue("jobId");
        UUID chapterId = (UUID) eventBean.getPropertyValue("chapterId");
        UUID bookId = (UUID) eventBean.getPropertyValue("bookId");

        log.info("[BOOK_INDIVIDUAL_REDUCTION] Starting automatic reduction for book={} after chapter={}", bookId, chapterId);

        BookIndividualResolutionResponse response = bookIndividualReductionService.resolveBook(bookId);
        eventPublisher.publishEvent(new BookIndividualsReducedEvent(
                this,
                jobId,
                chapterId,
                bookId,
                response.isProcessed(),
                response.getChapterIndividualCount(),
                response.getBookIndividualCount()
        ));
    }
}
