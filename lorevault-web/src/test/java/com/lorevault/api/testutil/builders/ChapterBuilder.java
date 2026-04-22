package com.lorevault.api.testutil.builders;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.PublicationCoordinates;
import com.lorevault.api.testutil.TestClock;
import com.lorevault.api.testutil.TestIds;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.lorevault.api.testutil.builders.PublicationCoordinatesBuilder.coordinates;

/**
 * Test builder for Chapter entities with deterministic defaults.
 */
public final class ChapterBuilder {
    
    private UUID id = TestIds.CHAPTER_ID;
    private UUID bookId = TestIds.BOOK_ID;
    private UUID universeId = TestIds.UNIVERSE_ID;
    private UUID seriesId = TestIds.SERIES_ID;
    private PublicationCoordinates coordinates = coordinates().build();
    private String chapterTitle = "Kaladin";
    private String rawText = "Kaladin stared at the spear in his hands...";
    private String contentHash = "test-content-hash";
    private LocalDateTime createdAt = LocalDateTime.now(TestClock.fixed());
    private LocalDateTime updatedAt = createdAt;
    
    private ChapterBuilder() {}
    
    public static ChapterBuilder aChapter() {
        return new ChapterBuilder();
    }
    
    /**
     * Create a chapter for a standalone book (no series).
     */
    public static ChapterBuilder aStandaloneChapter() {
        return new ChapterBuilder()
                .withSeriesId(null)
                .withCoordinates(PublicationCoordinatesBuilder.standaloneCoordinates().build())
                .withChapterTitle("The Shadow of Elantris")
                .withRawText("The city of Elantris was once beautiful...");
    }
    
    public ChapterBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public ChapterBuilder withBookId(UUID bookId) {
        this.bookId = bookId;
        return this;
    }
    
    public ChapterBuilder withUniverseId(UUID universeId) {
        this.universeId = universeId;
        return this;
    }
    
    public ChapterBuilder withSeriesId(UUID seriesId) {
        this.seriesId = seriesId;
        return this;
    }
    
    public ChapterBuilder withCoordinates(PublicationCoordinates coordinates) {
        this.coordinates = coordinates;
        return this;
    }
    
    public ChapterBuilder withChapterTitle(String chapterTitle) {
        this.chapterTitle = chapterTitle;
        return this;
    }
    
    public ChapterBuilder withRawText(String rawText) {
        this.rawText = rawText;
        return this;
    }
    
    public ChapterBuilder withContentHash(String contentHash) {
        this.contentHash = contentHash;
        return this;
    }
    
    public ChapterBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        return this;
    }
    
    public ChapterBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    public Chapter build() {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setBookId(bookId);
        chapter.setUniverseId(universeId);
        chapter.setSeriesId(seriesId);
        chapter.setCoordinates(coordinates);
        chapter.setChapterTitle(chapterTitle);
        chapter.setRawText(rawText);
        chapter.setContentHash(contentHash);
        chapter.setCreatedAt(createdAt);
        chapter.setUpdatedAt(updatedAt);
        return chapter;
    }
}
