package com.lorevault.api.content;
import com.lorevault.api.library.domain.Series;

import com.lorevault.api.testutil.TestIds;
import com.lorevault.api.testutil.builders.SeriesBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Series")
class SeriesTest {
    
    @Test
    @DisplayName("should create series with all required fields")
    void shouldCreateSeriesWithAllRequiredFields() {
        // Given
        UUID id = TestIds.SERIES_ID;
        UUID universeId = TestIds.UNIVERSE_ID;
        String universeName = TestIds.DEFAULT_UNIVERSE_NAME;
        String seriesName = "Mistborn";
        LocalDateTime ts = TestIds.FIXED_TIMESTAMP;
        
        // When
        Series series = SeriesBuilder.aSeries()
                .withId(id)
                .withUniverseId(universeId)
                .withUniverseName(universeName)
                .withName(seriesName)
                .withCreatedAt(ts)
                .withUpdatedAt(ts)
                .build();
        
        // Then
        assertThat(series.getId()).isEqualTo(id);
        assertThat(series.getUniverseId()).isEqualTo(universeId);
        assertThat(series.getUniverseName()).isEqualTo(universeName);
        assertThat(series.getName()).isEqualTo(seriesName);
        assertThat(series.getCreatedAt()).isEqualTo(ts);
        assertThat(series.getUpdatedAt()).isEqualTo(ts);
    }
    
    @Test
    @DisplayName("should create series using factory method")
    void shouldCreateSeriesUsingFactoryMethod() {
        // Given
        UUID universeId = TestIds.UNIVERSE_ID;
        String universeName = TestIds.DEFAULT_UNIVERSE_NAME;
        String seriesName = "Stormlight Archive";
        
        // When
        Series series = Series.create(universeId, universeName, seriesName);
        
        // Then
        assertThat(series.getId()).isNotNull();
        assertThat(series.getUniverseId()).isEqualTo(universeId);
        assertThat(series.getUniverseName()).isEqualTo(universeName);
        assertThat(series.getName()).isEqualTo(seriesName);
        assertThat(series.getUpdatedAt()).isEqualTo(series.getCreatedAt());
    }
    
    @Test
    @DisplayName("should use builder defaults for convenience")
    void shouldUseBuilderDefaultsForConvenience() {
        // When
        Series series = SeriesBuilder.aSeries().build();
        
        // Then
        assertThat(series.getId()).isEqualTo(TestIds.SERIES_ID);
        assertThat(series.getUniverseId()).isEqualTo(TestIds.UNIVERSE_ID);
        assertThat(series.getUniverseName()).isEqualTo("Cosmere");
        assertThat(series.getName()).isEqualTo("Stormlight Archive");
        assertThat(series.getCreatedAt()).isNotNull();
        assertThat(series.getUpdatedAt()).isNotNull();
    }
}
