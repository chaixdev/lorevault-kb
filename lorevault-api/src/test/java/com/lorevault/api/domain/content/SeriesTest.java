package com.lorevault.api.domain.content;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SeriesTest {

    @Test
    void create_setsAllFieldsCorrectly() {
        UUID universeId = UUID.randomUUID();
        String universeName = "Cosmere";
        String seriesName = "Stormlight Archive";
        
        Series series = Series.create(universeId, universeName, seriesName);
        
        assertNotNull(series.getId());
        assertEquals(universeId, series.getUniverseId());
        assertEquals(universeName, series.getUniverseName());
        assertEquals(seriesName, series.getName());
        assertNotNull(series.getCreatedAt());
        assertNotNull(series.getUpdatedAt());
    }

    @Test
    void create_handlesNullUniverseName() {
        UUID universeId = UUID.randomUUID();
        
        Series series = Series.create(universeId, null, "Standalone Series");
        
        assertEquals(universeId, series.getUniverseId());
        assertNull(series.getUniverseName());
        assertEquals("Standalone Series", series.getName());
    }

    @Test
    void create_ensuresUniqueIds() {
        UUID universeId = UUID.randomUUID();
        
        Series series1 = Series.create(universeId, "Universe", "Series A");
        Series series2 = Series.create(universeId, "Universe", "Series B");
        
        assertNotEquals(series1.getId(), series2.getId());
        assertEquals(universeId, series1.getUniverseId());
        assertEquals(universeId, series2.getUniverseId());
    }
}
