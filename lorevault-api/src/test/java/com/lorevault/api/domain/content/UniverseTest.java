package com.lorevault.api.domain.content;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class UniverseTest {

    @Test
    void ofName_setsSlugToSnakeCase() {
        Universe u = Universe.ofName("The Stormlight Archive");
        assertNotNull(u.getId());
        assertEquals("the_stormlight_archive", u.getSlug());
        assertEquals("The Stormlight Archive", u.getName());
        assertNotNull(u.getCreatedAt());
        assertNotNull(u.getUpdatedAt());
    }

    @Test
    void ofName_handlesSpecialCharacters() {
        Universe u = Universe.ofName("Warhammer 40,000: Horus Heresy");
        assertEquals("warhammer_40000_horus_heresy", u.getSlug());
        assertEquals("Warhammer 40,000: Horus Heresy", u.getName());
    }

    @Test
    void ofName_handlesEmptyAndBlankNames() {
        Universe blank = Universe.ofName("   ");
        assertEquals("", blank.getSlug());
        assertEquals("   ", blank.getName());

        Universe empty = Universe.ofName("");
        assertEquals("", empty.getSlug());
        assertEquals("", empty.getName());
    }

    @Test
    void constructor_setsFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        Universe u = new Universe(id, "Cosmere", "cosmere", now, now);
        
        assertEquals(id, u.getId());
        assertEquals("Cosmere", u.getName());
        assertEquals("cosmere", u.getSlug());
        assertEquals(now, u.getCreatedAt());
        assertEquals(now, u.getUpdatedAt());
    }
}
