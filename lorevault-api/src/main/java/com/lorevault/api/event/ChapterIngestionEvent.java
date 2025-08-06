package com.lorevault.api.event;

import org.springframework.context.ApplicationEvent;
import java.util.UUID;

public class ChapterIngestionEvent extends ApplicationEvent {
    private final UUID jobId;
    private final UUID chapterId;
    
    public ChapterIngestionEvent(Object source, UUID jobId, UUID chapterId) {
        super(source);
        this.jobId = jobId;
        this.chapterId = chapterId;
    }
    
    public UUID getJobId() { return jobId; }
    public UUID getChapterId() { return chapterId; }
}
