package com.lorevault.api.domain.content;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BookTest {

    @Test
    void displayLabel_formatsNicely() {
        Book b = new Book();
        b.setUniverse("Cosmere");
        b.setSeries("Stormlight Archive");
        b.setBookNumber(2);
        b.setTitle("Words of Radiance");

        assertEquals("Stormlight Archive #2 Words of Radiance", b.displayLabel());
    }

    @Test
    void displayLabel_handlesMissingFields() {
        Book b1 = new Book();
        b1.setTitle("Elantris");
        assertEquals("Elantris", b1.displayLabel());

        Book b2 = new Book();
        b2.setSeries("Mistborn");
        b2.setTitle("The Final Empire");
        assertEquals("Mistborn The Final Empire", b2.displayLabel());

        Book b3 = new Book();
        b3.setSeries("Mistborn");
        b3.setBookNumber(1);
        assertEquals("Mistborn #1", b3.displayLabel());
    }

    @Test
    void createInSeries_setsUuidReferencesAndDisplayMetadata() {
        UUID universeId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        
        Book book = Book.createInSeries(universeId, "Cosmere", seriesId, "Stormlight Archive", 1, "The Way of Kings");
        
        assertNotNull(book.getId());
        assertEquals(universeId, book.getUniverseId());
        assertEquals(seriesId, book.getSeriesId());
        assertEquals("Cosmere", book.getUniverse());
        assertEquals("Stormlight Archive", book.getSeries());
        assertEquals(Integer.valueOf(1), book.getBookNumber());
        assertEquals("The Way of Kings", book.getTitle());
        assertNotNull(book.getCreatedAt());
        assertNotNull(book.getUpdatedAt());
    }

    @Test
    void createStandalone_setsUniverseButNullSeries() {
        UUID universeId = UUID.randomUUID();
        
        Book book = Book.createStandalone(universeId, "Cosmere", "Elantris");
        
        assertNotNull(book.getId());
        assertEquals(universeId, book.getUniverseId());
        assertNull(book.getSeriesId());
        assertEquals("Cosmere", book.getUniverse());
        assertNull(book.getSeries());
        assertNull(book.getBookNumber());
        assertEquals("Elantris", book.getTitle());
    }

    @Test
    void createInSeries_validatesUuidRelationships() {
        UUID universeId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        
        Book book1 = Book.createInSeries(universeId, "Cosmere", seriesId, "Mistborn", 1, "The Final Empire");
        Book book2 = Book.createInSeries(universeId, "Cosmere", seriesId, "Mistborn", 2, "The Well of Ascension");
        
        // Same universe and series references
        assertEquals(universeId, book1.getUniverseId());
        assertEquals(universeId, book2.getUniverseId());
        assertEquals(seriesId, book1.getSeriesId());
        assertEquals(seriesId, book2.getSeriesId());
        
        // Different book IDs
        assertNotEquals(book1.getId(), book2.getId());
        
        // Different book numbers but same series
        assertEquals(Integer.valueOf(1), book1.getBookNumber());
        assertEquals(Integer.valueOf(2), book2.getBookNumber());
    }

    @Test
    void displayLabel_reflectsEntityRelationships() {
        UUID universeId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        
        Book seriesBook = Book.createInSeries(universeId, "Cosmere", seriesId, "Stormlight Archive", 1, "The Way of Kings");
        Book standalone = Book.createStandalone(universeId, "Cosmere", "Elantris");
        
        assertEquals("Stormlight Archive #1 The Way of Kings", seriesBook.displayLabel());
        assertEquals("Elantris", standalone.displayLabel());
    }
}
