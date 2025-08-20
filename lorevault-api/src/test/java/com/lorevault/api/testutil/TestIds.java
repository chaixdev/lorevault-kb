package com.lorevault.api.testutil;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic ID generator for reproducible test data.
 * Generates predictable UUIDs based on seeded input or counter.
 */
public final class TestIds {
    
    private static final AtomicLong counter = new AtomicLong(1);
    
    private TestIds() {}
    
    /**
     * Generates a deterministic UUID from a string seed.
     */
    public static UUID fromString(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Generates a sequential deterministic UUID using counter.
     */
    public static UUID next() {
        return fromString("test-id-" + counter.getAndIncrement());
    }
    
    /**
     * Resets the counter for test isolation.
     */
    public static void reset() {
        counter.set(1);
    }
    
    /**
     * Common test IDs for frequent use.
     */
    public static final class Common {
        public static final UUID UNIVERSE_ID = fromString("universe-cosmere");
        public static final UUID SERIES_ID = fromString("series-stormlight");
        public static final UUID BOOK_ID = fromString("book-way-of-kings");
        public static final UUID CHAPTER_ID = fromString("chapter-1");
        public static final UUID CHUNK_ID = fromString("chunk-1");
        
        private Common() {}
    }
}
