package com.lorevault.api.web.ui.view;

import com.lorevault.api.library.service.LibraryQueryService;

import java.util.UUID;

public record BookOption(UUID id, String label, String title, Integer bookNumber) {

    public static BookOption from(LibraryQueryService.BookSummary book) {
        if (book == null) {
            throw new IllegalArgumentException("Book cannot be null");
        }
        return new BookOption(book.id(), book.displayLabel(), book.title(), book.bookNumber());
    }
}
