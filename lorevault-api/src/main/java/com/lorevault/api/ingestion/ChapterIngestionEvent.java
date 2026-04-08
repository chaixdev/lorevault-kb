package com.lorevault.api.ingestion;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.UUID;

@Getter
public class ChapterIngestionEvent extends ApplicationEvent {
    private final UUID jobId;
    private final UUID chapterId;
    
    public ChapterIngestionEvent(Object source, UUID jobId, UUID chapterId) {
        super(source);
        this.jobId = jobId;
        this.chapterId = chapterId;
    }

}
