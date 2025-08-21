package com.lorevault.api.testutil.builders;

import com.lorevault.api.domain.content.Series;
import com.lorevault.api.testutil.TestClock;
import com.lorevault.api.testutil.TestIds;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test builder for Series entities with deterministic defaults.
 */
public final class SeriesBuilder {
    
    private UUID id = TestIds.SERIES_ID;
    private UUID universeId = TestIds.UNIVERSE_ID;
    private String universeName = "Cosmere";
    private String name = "Stormlight Archive";
    private LocalDateTime createdAt = LocalDateTime.now(TestClock.fixed());
    private LocalDateTime updatedAt = createdAt;
    
    private SeriesBuilder() {}
    
    public static SeriesBuilder aSeries() {
        return new SeriesBuilder();
    }
    
    public SeriesBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public SeriesBuilder withUniverseId(UUID universeId) {
        this.universeId = universeId;
        return this;
    }
    
    public SeriesBuilder withUniverseName(String universeName) {
        this.universeName = universeName;
        return this;
    }
    
    public SeriesBuilder withName(String name) {
        this.name = name;
        return this;
    }
    
    public SeriesBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        return this;
    }
    
    public SeriesBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    public Series build() {
        Series series = new Series();
        series.setId(id);
        series.setUniverseId(universeId);
        series.setUniverseName(universeName);
        series.setName(name);
        series.setCreatedAt(createdAt);
        series.setUpdatedAt(updatedAt);
        return series;
    }
}
