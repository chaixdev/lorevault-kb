package com.lorevault.api.testutil;

import com.lorevault.api.content.domain.PublicationCoordinates;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Deterministic test IDs and coordinates for reproducible tests.
 * All IDs are predictable and consistent across test runs.
 */
public class TestIds {
    
    // Base seed for deterministic UUID generation
    private static final long SEED_BASE = 12345L;
    
    // Deterministic UUIDs for different entity types
    public static final UUID UNIVERSE_ID = generateDeterministicUUID(SEED_BASE + 1);
    public static final UUID SERIES_ID = generateDeterministicUUID(SEED_BASE + 2);
    public static final UUID BOOK_ID = generateDeterministicUUID(SEED_BASE + 3);
    public static final UUID CHAPTER_ID = generateDeterministicUUID(SEED_BASE + 4);
    
    // Alternative IDs for multi-entity tests
    public static final UUID UNIVERSE_ID_2 = generateDeterministicUUID(SEED_BASE + 11);
    public static final UUID SERIES_ID_2 = generateDeterministicUUID(SEED_BASE + 12);
    public static final UUID BOOK_ID_2 = generateDeterministicUUID(SEED_BASE + 13);
    public static final UUID CHAPTER_ID_2 = generateDeterministicUUID(SEED_BASE + 14);
    
    // Common test strings
    public static final String DEFAULT_UNIVERSE_NAME = "Test Universe";
    public static final String DEFAULT_SERIES_NAME = "Test Series";
    public static final String DEFAULT_BOOK_TITLE = "Test Book";
    public static final String DEFAULT_CHAPTER_TITLE = "Test Chapter";
    
    // Publication coordinates
    public static final PublicationCoordinates DEFAULT_PUBLICATION_COORDS = new PublicationCoordinates(
            DEFAULT_UNIVERSE_NAME,
            DEFAULT_SERIES_NAME,
            DEFAULT_BOOK_TITLE,
            DEFAULT_CHAPTER_TITLE,
            1, // book number
            1  // chapter number
    );
    
    // Timestamps
    public static final LocalDateTime FIXED_TIMESTAMP = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
    
    /**
     * Generate a deterministic UUID from a seed value.
     * Same seed always produces the same UUID.
     */
    public static UUID generateDeterministicUUID(long seed) {
        // Use seed to create deterministic most/least significant bits
        long mostSigBits = seed ^ (seed << 32);
        long leastSigBits = seed ^ (seed >>> 32);
        return new UUID(mostSigBits, leastSigBits);
    }
    
    /**
     * Generate a series of deterministic UUIDs.
     */
    public static UUID[] generateUUIDs(int count) {
        UUID[] uuids = new UUID[count];
        for (int i = 0; i < count; i++) {
            uuids[i] = generateDeterministicUUID(SEED_BASE + 100 + i);
        }
        return uuids;
    }
    
    private TestIds() {
        // Utility class - no instantiation
    }
}