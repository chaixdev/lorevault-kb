package com.lorevault.api.testutil.builders;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.content.Universe;
import com.lorevault.api.testutil.TestClock;
import com.lorevault.api.testutil.TestIds;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.lorevault.api.content.StringSanitizer.toSnakeCase;

/**
 * Test builder for Universe entities with deterministic defaults.
 */
public final class UniverseBuilder {
    
    private UUID id = TestIds.UNIVERSE_ID;
    private String name = "Cosmere";
    private String slug = "cosmere";
    private LocalDateTime createdAt = LocalDateTime.now(TestClock.fixed());
    private LocalDateTime updatedAt = createdAt;
    
    private UniverseBuilder() {}
    
    public static UniverseBuilder aUniverse() {
        return new UniverseBuilder();
    }
    
    public UniverseBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public UniverseBuilder withName(String name) {
        this.name = name;
        return this;
    }
    
    public UniverseBuilder withSlug(String slug) {
        this.slug = slug;
        return this;
    }
    
    public UniverseBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt; // sync updated time by default
        return this;
    }
    
    public UniverseBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    public Universe build() {
        Universe universe = new Universe();
        universe.setId(id);
        universe.setName(name);
        universe.setSlug(slug);
        universe.setCreatedAt(createdAt);
        universe.setUpdatedAt(updatedAt);
        return universe;
    }
}
