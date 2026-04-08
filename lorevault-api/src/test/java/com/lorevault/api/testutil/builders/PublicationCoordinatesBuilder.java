package com.lorevault.api.testutil.builders;

import com.lorevault.api.support.PublicationCoordinates;

/**
 * Test builder for PublicationCoordinates with deterministic defaults.
 */
public final class PublicationCoordinatesBuilder {
    
    private String universe = "Cosmere";
    private String series = "Stormlight Archive";
    private String bookTitle = "The Way of Kings";
    private String chapterTitle = "Kaladin";
    private Integer bookNumber = 1;
    private Integer chapterNumber = 1;
    
    private PublicationCoordinatesBuilder() {}
    
    public static PublicationCoordinatesBuilder coordinates() {
        return new PublicationCoordinatesBuilder();
    }
    
    /**
     * Create coordinates for a standalone book (no series).
     */
    public static PublicationCoordinatesBuilder standaloneCoordinates() {
        return new PublicationCoordinatesBuilder()
                .withSeries(null)
                .withBookTitle("Elantris")
                .withChapterTitle("The Shadow of Elantris");
    }
    
    public PublicationCoordinatesBuilder withUniverse(String universe) {
        this.universe = universe;
        return this;
    }
    
    public PublicationCoordinatesBuilder withSeries(String series) {
        this.series = series;
        return this;
    }
    
    public PublicationCoordinatesBuilder withBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
        return this;
    }
    
    public PublicationCoordinatesBuilder withChapterTitle(String chapterTitle) {
        this.chapterTitle = chapterTitle;
        return this;
    }
    
    public PublicationCoordinatesBuilder withBookNumber(Integer bookNumber) {
        this.bookNumber = bookNumber;
        return this;
    }
    
    public PublicationCoordinatesBuilder withChapterNumber(Integer chapterNumber) {
        this.chapterNumber = chapterNumber;
        return this;
    }
    
    public PublicationCoordinates build() {
        return new PublicationCoordinates(
            universe, series, bookTitle, chapterTitle, bookNumber, chapterNumber
        );
    }
}
