package com.lorevault.api.web.command.ingestion;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class BookIndividualResolutionResponse {

    private UUID bookId;
    private boolean processed;
    private int chapterIndividualCount;
    private int bookIndividualCount;
    private String message;

    public BookIndividualResolutionResponse(
            UUID bookId,
            boolean processed,
            int chapterIndividualCount,
            int bookIndividualCount,
            String message
    ) {
        this.bookId = bookId;
        this.processed = processed;
        this.chapterIndividualCount = chapterIndividualCount;
        this.bookIndividualCount = bookIndividualCount;
        this.message = message;
    }

}
