package com.lorevault.api.content;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.testutil.TestIds;
import com.lorevault.api.testutil.builders.UniverseBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Universe domain entity.
 * Focus: Core business logic, value object behavior, invariants.
 */
@Tag("unit")
@DisplayName("Universe")
class UniverseTest {
    
    @Test
    @DisplayName("should create universe with all required fields")
    void shouldCreateUniverseWithAllRequiredFields() {
        // Given
        UUID id = TestIds.UNIVERSE_ID;
        String name = "Cosmere";
        String slug = "cosmere";
        LocalDateTime timestamp = TestIds.FIXED_TIMESTAMP;
        
        // When
        Universe universe = UniverseBuilder.aUniverse()
                .withId(id)
                .withName(name)
                .withSlug(slug)
                .withCreatedAt(timestamp)
                .withUpdatedAt(timestamp)
                .build();
        
        // Then
        assertThat(universe.getId()).isEqualTo(id);
        assertThat(universe.getName()).isEqualTo(name);
        assertThat(universe.getSlug()).isEqualTo(slug);
        assertThat(universe.getCreatedAt()).isEqualTo(timestamp);
        assertThat(universe.getUpdatedAt()).isEqualTo(timestamp);
    }
    
    @Test
    @DisplayName("should create universe using ofName factory method")
    void shouldCreateUniverseUsingOfNameFactory() {
        // Given
        String name = "Wheel of Time";
        
        // When
        Universe universe = Universe.ofName(name);
        
        // Then
        assertThat(universe.getName()).isEqualTo(name);
        assertThat(universe.getSlug()).isEqualTo("wheel_of_time");
        assertThat(universe.getId()).isNotNull();
        assertThat(universe.getCreatedAt()).isNotNull();
        assertThat(universe.getUpdatedAt()).isEqualTo(universe.getCreatedAt());
    }
    
    @Test
    @DisplayName("should use builder defaults for convenience")
    void shouldUseBuilderDefaultsForConvenience() {
        // When
        Universe universe = UniverseBuilder.aUniverse().build();
        
        // Then - should use deterministic defaults
        assertThat(universe.getId()).isEqualTo(TestIds.UNIVERSE_ID);
        assertThat(universe.getName()).isEqualTo("Cosmere");
        assertThat(universe.getSlug()).isEqualTo("cosmere");
        assertThat(universe.getCreatedAt()).isNotNull();
        assertThat(universe.getUpdatedAt()).isNotNull();
    }
    
    @Test
    @DisplayName("should allow fluent customization of any field")
    void shouldAllowFluentCustomizationOfAnyField() {
        // Given
        String customName = "Custom Universe";
        LocalDateTime customTime = TestIds.FIXED_TIMESTAMP.plusDays(5);
        
        // When
        Universe universe = UniverseBuilder.aUniverse()
                .withName(customName)
                .withSlug("custom_slug")
                .withCreatedAt(customTime)
                .build();
        
        // Then
        assertThat(universe.getName()).isEqualTo(customName);
        assertThat(universe.getSlug()).isEqualTo("custom_slug");
        assertThat(universe.getCreatedAt()).isEqualTo(customTime);
    }
}
