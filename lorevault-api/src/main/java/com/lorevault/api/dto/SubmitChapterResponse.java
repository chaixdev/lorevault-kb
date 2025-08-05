package com.lorevault.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for chapter submission
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitChapterResponse {

    /**
     * The unique identifier of the created ingestion job
     */
    private UUID jobId;

    /**
     * The unique identifier of the created chapter
     */
    private UUID chapterId;

    /**
     * Human-readable message about the submission
     */
    private String message;

    public static SubmitChapterResponse success(UUID jobId, UUID chapterId) {
        return new SubmitChapterResponse(
            jobId, 
            chapterId, 
            "Chapter submitted successfully for processing"
        );
    }
}
