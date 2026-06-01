package com.lorevault.api.orchestration.consolidation;

import java.util.UUID;

public class BookConsolidationClaimUnavailableException extends RuntimeException {

    private final UUID bookId;

    public BookConsolidationClaimUnavailableException(String stage, UUID bookId) {
        super(stage + " claim unavailable for bookId=" + bookId);
        this.bookId = bookId;
    }

    public UUID getBookId() {
        return bookId;
    }
}
