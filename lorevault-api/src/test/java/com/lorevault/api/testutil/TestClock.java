package com.lorevault.api.testutil;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Deterministic clock for tests to ensure reproducible timing.
 */
public final class TestClock {
    
    private static final ZonedDateTime DEFAULT_FIXED_TIME = 
        ZonedDateTime.of(2025, 1, 15, 14, 30, 0, 0, ZoneId.of("UTC"));
    
    private TestClock() {}
    
    /**
     * Returns a fixed clock at a predictable timestamp.
     */
    public static Clock fixed() {
        return Clock.fixed(DEFAULT_FIXED_TIME.toInstant(), ZoneId.of("UTC"));
    }
    
    /**
     * Returns a fixed clock at the specified instant.
     */
    public static Clock fixedAt(Instant instant) {
        return Clock.fixed(instant, ZoneId.of("UTC"));
    }
    
    /**
     * Returns a fixed clock at the specified date/time.
     */
    public static Clock fixedAt(ZonedDateTime dateTime) {
        return Clock.fixed(dateTime.toInstant(), dateTime.getZone());
    }
    
    /**
     * Returns the default fixed instant used in tests.
     */
    public static Instant defaultInstant() {
        return DEFAULT_FIXED_TIME.toInstant();
    }
}
