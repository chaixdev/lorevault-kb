package com.lorevault.api.testutil.builders;

import com.lorevault.api.library.book.Book;
import com.lorevault.api.testutil.TestClock;
import com.lorevault.api.testutil.TestIds;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test builder for Book entities with deterministic defaults.
 */
public final class BookBuilder {
    
    private UUID id = TestIds.BOOK_ID;
    private UUID universeId = TestIds.UNIVERSE_ID;
    private UUID seriesId = TestIds.SERIES_ID;
    private String universe = "Cosmere";
    private String series = "Stormlight Archive";
    private Integer bookNumber = 1;
    private String title = "The Way of Kings";
    private LocalDateTime createdAt = LocalDateTime.now(TestClock.fixed());
    private LocalDateTime updatedAt = createdAt;
    
    private BookBuilder() {}
    
    public static BookBuilder aBook() {
        return new BookBuilder();
    }
    
    /**
     * Create a standalone book (no series).
     */
    public static BookBuilder aStandaloneBook() {
        return new BookBuilder()
                .withSeriesId(null)
                .withSeries(null)
                .withBookNumber(null)
                .withTitle("Elantris");
    }
    
    public BookBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public BookBuilder withUniverseId(UUID universeId) {
        this.universeId = universeId;
        return this;
    }
    
    public BookBuilder withSeriesId(UUID seriesId) {
        this.seriesId = seriesId;
        return this;
    }
    
    public BookBuilder withUniverse(String universe) {
        this.universe = universe;
        return this;
    }
    
    public BookBuilder withSeries(String series) {
        this.series = series;
        return this;
    }
    
    public BookBuilder withBookNumber(Integer bookNumber) {
        this.bookNumber = bookNumber;
        return this;
    }
    
    public BookBuilder withTitle(String title) {
        this.title = title;
        return this;
    }
    
    public BookBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        return this;
    }
    
    public BookBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    public Book build() {
        Book book = new Book();
        book.setId(id);
        book.setUniverseId(universeId);
        book.setSeriesId(seriesId);
        book.setUniverse(universe);
        book.setSeries(series);
        book.setBookNumber(bookNumber);
        book.setTitle(title);
        book.setCreatedAt(createdAt);
        book.setUpdatedAt(updatedAt);
        return book;
    }
}
