package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class EmbeddingsCompletedEvent extends IngestionEvent {

    private final int totalScenes;
    private final int totalChunks;
    private final int totalEmbeddings;
    private final int chapterLength;

    public EmbeddingsCompletedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            int totalScenes,
            int totalChunks,
            int totalEmbeddings,
            int chapterLength
    ) {
        super(source, jobId, chapterId);
        this.totalScenes = totalScenes;
        this.totalChunks = totalChunks;
        this.totalEmbeddings = totalEmbeddings;
        this.chapterLength = chapterLength;
    }

    @Override
    public String getEventType() {
        return "EMBEDDINGS_COMPLETED";
    }
}
