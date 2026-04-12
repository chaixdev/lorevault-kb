package com.lorevault.api.ingestion;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.lorevault.api.support.ChapterIndividualResolutionResponse;

@Component
public class ChapterIndividualResolutionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChapterIndividualResolutionHandler.class);

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;
    private final ApplicationEventPublisher eventPublisher;

    public ChapterIndividualResolutionHandler(
            ChapterIndividualResolutionService chapterIndividualResolutionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterIndividualResolutionService = chapterIndividualResolutionService;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        UUID chapterId = (UUID) eventBean.getPropertyValue("chapterId");
        UUID jobId = (UUID) eventBean.getPropertyValue("jobId");
        UUID bookId = (UUID) eventBean.getPropertyValue("bookId");
        log.info("[CHAPTER_INDIVIDUAL_RESOLUTION] Starting automatic resolution for chapter={}", chapterId);
        ChapterIndividualResolutionResponse response = chapterIndividualResolutionService.resolveChapter(chapterId);
        eventPublisher.publishEvent(new ChapterIndividualsResolvedEvent(
                this,
                jobId,
                chapterId,
                bookId,
                response.isProcessed(),
                response.getMentionCount(),
                response.getChapterIndividualCount()
        ));
    }
}
