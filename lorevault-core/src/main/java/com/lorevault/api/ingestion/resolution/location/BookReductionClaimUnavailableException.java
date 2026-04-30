package com.lorevault.api.ingestion.resolution.location;

import java.util.UUID;

public class BookReductionClaimUnavailableException extends RuntimeException {

    private final UUID bookId;

    public BookReductionClaimUnavailableException(String stage, UUID bookId) {
        super(stage + " claim unavailable for bookId=" + bookId);
        this.bookId = bookId;
    }

    public UUID getBookId() {
        return bookId;
    }
}
