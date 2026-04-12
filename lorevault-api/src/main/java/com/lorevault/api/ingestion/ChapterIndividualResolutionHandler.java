package com.lorevault.api.ingestion;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ChapterIndividualResolutionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChapterIndividualResolutionHandler.class);

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;

    public ChapterIndividualResolutionHandler(ChapterIndividualResolutionService chapterIndividualResolutionService) {
        this.chapterIndividualResolutionService = chapterIndividualResolutionService;
    }

    @Async
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = (UUID) new BeanWrapperImpl(event).getPropertyValue("chapterId");
        log.info("[CHAPTER_INDIVIDUAL_RESOLUTION] Starting automatic resolution for chapter={}", chapterId);
        chapterIndividualResolutionService.resolveChapter(chapterId);
    }
}
