package com.lorevault.api.domain.content;

import com.lorevault.api.domain.shared.PublicationCoordinates;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ChapterTest {

    @Test
    void createWithReferences_setsAllUuidReferencesAndCoordinates() {
        UUID bookId = UUID.randomUUID();
        UUID universeId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        
        PublicationCoordinates coords = new PublicationCoordinates(
            "Cosmere", "Stormlight Archive", "The Way of Kings", "The King's Feast", 1, 5
        );
        
        Chapter chapter = Chapter.createWithReferences(
            bookId, universeId, seriesId, coords, 
            "The King's Feast", "Raw chapter text", "hash123"
        );
        
        assertNotNull(chapter.getId());
        assertEquals(bookId, chapter.getBookId());
        assertEquals(universeId, chapter.getUniverseId());
        assertEquals(seriesId, chapter.getSeriesId());
        assertEquals(coords, chapter.getCoordinates());
        assertEquals("The King's Feast", chapter.getChapterTitle());
        assertEquals("Raw chapter text", chapter.getRawText());
        assertEquals("hash123", chapter.getContentHash());
        assertNotNull(chapter.getCreatedAt());
        assertNotNull(chapter.getUpdatedAt());
    }

    @Test
    void createStandalone_setsNullSeriesId() {
        UUID bookId = UUID.randomUUID();
        UUID universeId = UUID.randomUUID();
        
        PublicationCoordinates coords = new PublicationCoordinates(
            "Cosmere", null, "Elantris", "Chapter 1", null, 1
        );
        
        Chapter chapter = Chapter.createStandalone(
            bookId, universeId, coords, "Chapter 1", "Raw text", "hash456"
        );
        
        assertEquals(bookId, chapter.getBookId());
        assertEquals(universeId, chapter.getUniverseId());
        assertNull(chapter.getSeriesId()); // Standalone book
        assertEquals(coords, chapter.getCoordinates());
    }

    @Test
    void createWithReferences_preservesSpoilerGatingCapability() {
        UUID bookId = UUID.randomUUID();
        UUID universeId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        
        PublicationCoordinates coords1 = new PublicationCoordinates(
            "Cosmere", "Stormlight Archive", "The Way of Kings", "The Glory of the First", 1, 1
        );
        PublicationCoordinates coords2 = new PublicationCoordinates(
            "Cosmere", "Stormlight Archive", "Words of Radiance", "The Shattered Plains", 2, 15
        );
        
        Chapter chapter1 = Chapter.createWithReferences(
            bookId, universeId, seriesId, coords1, "The Glory of the First", "Text1", "hash1"
        );
        Chapter chapter2 = Chapter.createWithReferences(
            bookId, universeId, seriesId, coords2, "The Shattered Plains", "Text2", "hash2"
        );
        
        // UUID references for graph relationships
        assertEquals(universeId, chapter1.getUniverseId());
        assertEquals(seriesId, chapter1.getSeriesId());
        assertEquals(bookId, chapter1.getBookId());
        
        // PublicationCoordinates for spoiler gating (ordering)
        assertEquals(Integer.valueOf(1), chapter1.getCoordinates().getBookNumber());
        assertEquals(Integer.valueOf(1), chapter1.getCoordinates().getChapterNumber());
        assertEquals(Integer.valueOf(2), chapter2.getCoordinates().getBookNumber());
        assertEquals(Integer.valueOf(15), chapter2.getCoordinates().getChapterNumber());
        
        // Spoiler gating logic can compare: chapter2 > chapter1
        assertTrue(chapter2.getCoordinates().getBookNumber() > chapter1.getCoordinates().getBookNumber());
    }

    @Test
    void factoryMethods_ensureUniqueChapterIds() {
        UUID bookId = UUID.randomUUID();
        UUID universeId = UUID.randomUUID();
        
        PublicationCoordinates coords = new PublicationCoordinates(
            "Cosmere", null, "Elantris", "Chapter 1", null, 1
        );
        
        Chapter chapter1 = Chapter.createStandalone(bookId, universeId, coords, "Chapter 1", "Text", "hash1");
        Chapter chapter2 = Chapter.createStandalone(bookId, universeId, coords, "Chapter 2", "Text", "hash2");
        
        assertNotEquals(chapter1.getId(), chapter2.getId());
        assertEquals(bookId, chapter1.getBookId());
        assertEquals(bookId, chapter2.getBookId());
    }
}
