package com.lorevault.api.domain.content;

import com.lorevault.api.domain.shared.PublicationCoordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests validating the complete entity relationship model:
 * Universe -> Series -> Book -> Chapter with both UUID references and PublicationCoordinates.
 */
public class ContentEntityIntegrationTest {

    private Universe cosmere;
    private Series stormlightArchive;
    private Book wayOfKings;
    private Book elantris;

    @BeforeEach
    void setUp() {
        // Create universe
        cosmere = Universe.ofName("Cosmere");
        
        // Create series within universe
        stormlightArchive = Series.create(cosmere.getId(), cosmere.getName(), "Stormlight Archive");
        
        // Create books
        wayOfKings = Book.createInSeries(
            cosmere.getId(), cosmere.getName(), 
            stormlightArchive.getId(), stormlightArchive.getName(),
            1, "The Way of Kings"
        );
        
        elantris = Book.createStandalone(cosmere.getId(), cosmere.getName(), "Elantris");
    }

    @Test
    void entityHierarchy_maintainsConsistentUuidReferences() {
        // Universe -> Series relationship
        assertEquals(cosmere.getId(), stormlightArchive.getUniverseId());
        assertEquals(cosmere.getName(), stormlightArchive.getUniverseName());
        
        // Universe -> Series -> Book relationship
        assertEquals(cosmere.getId(), wayOfKings.getUniverseId());
        assertEquals(stormlightArchive.getId(), wayOfKings.getSeriesId());
        assertEquals(cosmere.getName(), wayOfKings.getUniverse());
        assertEquals(stormlightArchive.getName(), wayOfKings.getSeries());
        
        // Universe -> Book (standalone) relationship
        assertEquals(cosmere.getId(), elantris.getUniverseId());
        assertNull(elantris.getSeriesId()); // Standalone
        assertEquals(cosmere.getName(), elantris.getUniverse());
        assertNull(elantris.getSeries());
    }

    @Test
    void chapterCreation_integratesUuidReferencesWithPublicationCoordinates() {
        // Create chapter with full reference chain
        PublicationCoordinates coords = new PublicationCoordinates(
            "Cosmere", "Stormlight Archive", "The Way of Kings", "The Glory of the First", 1, 1
        );
        
        Chapter chapter = Chapter.createWithReferences(
            wayOfKings.getId(), 
            cosmere.getId(), 
            stormlightArchive.getId(),
            coords,
            "The Glory of the First",
            "Raw chapter text",
            "content-hash"
        );
        
        // UUID references for graph relationships
        assertEquals(wayOfKings.getId(), chapter.getBookId());
        assertEquals(cosmere.getId(), chapter.getUniverseId());
        assertEquals(stormlightArchive.getId(), chapter.getSeriesId());
        
        // PublicationCoordinates for spoiler gating and display
        assertEquals("Cosmere", chapter.getCoordinates().getUniverse());
        assertEquals("Stormlight Archive", chapter.getCoordinates().getSeries());
        assertEquals("The Way of Kings", chapter.getCoordinates().getBookTitle());
        assertEquals(Integer.valueOf(1), chapter.getCoordinates().getBookNumber());
        assertEquals(Integer.valueOf(1), chapter.getCoordinates().getChapterNumber());
    }

    @Test
    void standaloneBookChapter_handlesNullSeriesCorrectly() {
        PublicationCoordinates coords = new PublicationCoordinates(
            "Cosmere", null, "Elantris", "Chapter One", null, 1
        );
        
        Chapter chapter = Chapter.createStandalone(
            elantris.getId(),
            cosmere.getId(),
            coords,
            "Chapter One",
            "In the palace of Elantris...",
            "elantris-ch1-hash"
        );
        
        // UUID references
        assertEquals(elantris.getId(), chapter.getBookId());
        assertEquals(cosmere.getId(), chapter.getUniverseId());
        assertNull(chapter.getSeriesId()); // Standalone book
        
        // PublicationCoordinates handle null series
        assertEquals("Cosmere", chapter.getCoordinates().getUniverse());
        assertNull(chapter.getCoordinates().getSeries());
        assertEquals("Elantris", chapter.getCoordinates().getBookTitle());
    }

    @Test
    void spoilerGating_worksWithUuidBasedModel() {
        // Create chapters from different books for spoiler comparison
        PublicationCoordinates book1Ch1 = new PublicationCoordinates(
            "Cosmere", "Stormlight Archive", "The Way of Kings", "Prologue", 1, 1
        );
        PublicationCoordinates book1Ch50 = new PublicationCoordinates(
            "Cosmere", "Stormlight Archive", "The Way of Kings", "The Approach", 1, 50
        );
        PublicationCoordinates book2Ch1 = new PublicationCoordinates(
            "Cosmere", "Stormlight Archive", "Words of Radiance", "New Beginnings", 2, 1
        );
        
        Chapter prologue = Chapter.createWithReferences(
            wayOfKings.getId(), cosmere.getId(), stormlightArchive.getId(),
            book1Ch1, "Prologue", "Kalak", "hash1"
        );
        Chapter approach = Chapter.createWithReferences(
            wayOfKings.getId(), cosmere.getId(), stormlightArchive.getId(),
            book1Ch50, "The Approach", "Dalinar", "hash50"
        );
        
        // Create second book for cross-book comparison
        Book wordsOfRadiance = Book.createInSeries(
            cosmere.getId(), cosmere.getName(),
            stormlightArchive.getId(), stormlightArchive.getName(),
            2, "Words of Radiance"
        );
        Chapter newBeginnings = Chapter.createWithReferences(
            wordsOfRadiance.getId(), cosmere.getId(), stormlightArchive.getId(),
            book2Ch1, "New Beginnings", "Shallan", "hash2-1"
        );
        
        // Spoiler gating logic using PublicationCoordinates
        assertTrue(approach.getCoordinates().getChapterNumber() > prologue.getCoordinates().getChapterNumber());
        assertTrue(newBeginnings.getCoordinates().getBookNumber() > prologue.getCoordinates().getBookNumber());
        assertTrue(newBeginnings.getCoordinates().getBookNumber() > approach.getCoordinates().getBookNumber());
        
        // Graph relationships using UUIDs remain consistent
        assertEquals(wayOfKings.getId(), prologue.getBookId());
        assertEquals(wayOfKings.getId(), approach.getBookId());
        assertEquals(wordsOfRadiance.getId(), newBeginnings.getBookId());
        
        // All belong to same universe and series
        assertEquals(cosmere.getId(), prologue.getUniverseId());
        assertEquals(cosmere.getId(), newBeginnings.getUniverseId());
        assertEquals(stormlightArchive.getId(), prologue.getSeriesId());
        assertEquals(stormlightArchive.getId(), newBeginnings.getSeriesId());
    }

    @Test
    void displayAndIdentity_workTogether() {
        // Display uses human-readable names
        assertEquals("Stormlight Archive #1 The Way of Kings", wayOfKings.displayLabel());
        assertEquals("Elantris", elantris.displayLabel());
        assertEquals("cosmere", cosmere.getSlug());
        
        // Identity uses stable UUIDs
        assertNotNull(cosmere.getId());
        assertNotNull(stormlightArchive.getId());
        assertNotNull(wayOfKings.getId());
        assertNotNull(elantris.getId());
        
        // IDs are unique even for similar content
        Book anotherElantris = Book.createStandalone(cosmere.getId(), cosmere.getName(), "Elantris");
        assertNotEquals(elantris.getId(), anotherElantris.getId());
        assertEquals(elantris.displayLabel(), anotherElantris.displayLabel());
    }
}
