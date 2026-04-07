package com.lorevault.api.service.library;

import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.dto.library.*;
import com.lorevault.api.testutil.fakes.FakeContentPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("service")
@DisplayName("LibraryService")
class LibraryServiceTest {

    private FakeContentPersistencePort contentPersistencePort;
    private LibraryService catalogService;

    @BeforeEach
    void setUp() {
        contentPersistencePort = new FakeContentPersistencePort();
        catalogService = new LibraryService(contentPersistencePort.asUniverseRepo(), contentPersistencePort.asSeriesRepo(), contentPersistencePort.asBookRepo());
    }

    @DisplayName("Universe Creation")
    @Test
    void shouldCreateNewUniverse() {
        // Given
        CreateUniverseRequest request = new CreateUniverseRequest("Cosmere");

        // When
        CreateUniverseResponse response = catalogService.createUniverse(request);

        // Then
        assertThat(response.getUniverseId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Cosmere");
        assertThat(response.getSlug()).isEqualTo("cosmere");
        assertThat(response.isCreated()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        
        // Verify stored in persistence
        assertThat(contentPersistencePort.findUniverseById(response.getUniverseId())).isPresent();
    }

    @Test
    void shouldReturnExistingUniverseWhenNameAlreadyExists() {
        // Given
        Universe existingUniverse = Universe.ofName("Cosmere");
        contentPersistencePort.createUniverse(existingUniverse);
        
        CreateUniverseRequest request = new CreateUniverseRequest("Cosmere");

        // When
        CreateUniverseResponse response = catalogService.createUniverse(request);

        // Then
        assertThat(response.getUniverseId()).isEqualTo(existingUniverse.getId());
        assertThat(response.getName()).isEqualTo("Cosmere");
        assertThat(response.getSlug()).isEqualTo("cosmere");
        assertThat(response.isCreated()).isFalse();
        assertThat(response.getCreatedAt()).isEqualTo(existingUniverse.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(existingUniverse.getUpdatedAt());
    }

    @Test
    void shouldThrowExceptionWhenUniverseNameIsNull() {
        // Given
        CreateUniverseRequest request = new CreateUniverseRequest(null);

        // When & Then
        assertThatThrownBy(() -> catalogService.createUniverse(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe name cannot be null or blank");
    }

    @Test
    void shouldThrowExceptionWhenUniverseNameIsBlank() {
        // Given
        CreateUniverseRequest request = new CreateUniverseRequest("   ");

        // When & Then
        assertThatThrownBy(() -> catalogService.createUniverse(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe name cannot be null or blank");
    }

    @DisplayName("Series Creation")
    @Test
    void shouldCreateNewSeriesInUniverse() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistencePort.createUniverse(universe);
        
        CreateSeriesRequest request = new CreateSeriesRequest(universe.getId(), "Stormlight Archive");

        // When
        CreateSeriesResponse response = catalogService.createSeries(request);

        // Then
        assertThat(response.getSeriesId()).isNotNull();
        assertThat(response.getUniverseId()).isEqualTo(universe.getId());
        assertThat(response.getUniverseName()).isEqualTo("Cosmere");
        assertThat(response.getName()).isEqualTo("Stormlight Archive");
        assertThat(response.isCreated()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        
        // Verify stored in persistence
        assertThat(contentPersistencePort.findSeriesById(response.getSeriesId())).isPresent();
    }

    @Test
    void shouldReturnExistingSeriesWhenNameAlreadyExistsInUniverse() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistencePort.createUniverse(universe);
        
        Series existingSeries = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        contentPersistencePort.createSeries(existingSeries);
        
        CreateSeriesRequest request = new CreateSeriesRequest(universe.getId(), "Stormlight Archive");

        // When
        CreateSeriesResponse response = catalogService.createSeries(request);

        // Then
        assertThat(response.getSeriesId()).isEqualTo(existingSeries.getId());
        assertThat(response.getUniverseId()).isEqualTo(universe.getId());
        assertThat(response.getName()).isEqualTo("Stormlight Archive");
        assertThat(response.isCreated()).isFalse();
    }

    @Test
    void shouldAllowSameSeriesNameInDifferentUniverses() {
        // Given
        Universe cosmere = Universe.ofName("Cosmere");
        Universe marvel = Universe.ofName("Marvel");
        contentPersistencePort.createUniverse(cosmere);
        contentPersistencePort.createUniverse(marvel);
        
        Series cosmereSeries = Series.create(cosmere.getId(), "Cosmere", "Foundation");
        contentPersistencePort.createSeries(cosmereSeries);
        
        CreateSeriesRequest request = new CreateSeriesRequest(marvel.getId(), "Foundation");

        // When
        CreateSeriesResponse response = catalogService.createSeries(request);

        // Then
        assertThat(response.getSeriesId()).isNotEqualTo(cosmereSeries.getId());
        assertThat(response.getUniverseId()).isEqualTo(marvel.getId());
        assertThat(response.getName()).isEqualTo("Foundation");
        assertThat(response.isCreated()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenUniverseNotFound() {
        // Given
        UUID nonExistentUniverseId = UUID.randomUUID();
        CreateSeriesRequest request = new CreateSeriesRequest(nonExistentUniverseId, "Stormlight Archive");

        // When & Then
        assertThatThrownBy(() -> catalogService.createSeries(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe not found")
                .hasMessageContaining(nonExistentUniverseId.toString());
    }

    @DisplayName("Book Creation")
    @Test
    void shouldCreateStandaloneBookInUniverse() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistencePort.createUniverse(universe);
        
        CreateBookRequest request = CreateBookRequest.standalone(universe.getId(), "Warbreaker");

        // When
        CreateBookResponse response = catalogService.createBook(request);

        // Then
        assertThat(response.getBookId()).isNotNull();
        assertThat(response.getUniverseId()).isEqualTo(universe.getId());
        assertThat(response.getUniverseName()).isEqualTo("Cosmere");
        assertThat(response.getSeriesId()).isNull();
        assertThat(response.getSeriesName()).isNull();
        assertThat(response.getTitle()).isEqualTo("Warbreaker");
        assertThat(response.getBookNumber()).isNull();
        assertThat(response.isCreated()).isTrue();
        
        // Verify stored in persistence
        assertThat(contentPersistencePort.findBookById(response.getBookId())).isPresent();
    }

    @Test
    void shouldCreateBookInSeries() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        Series series = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        contentPersistencePort.createUniverse(universe);
        contentPersistencePort.createSeries(series);
        
        CreateBookRequest request = CreateBookRequest.inSeries(universe.getId(), series.getId(), "The Way of Kings", 1);

        // When
        CreateBookResponse response = catalogService.createBook(request);

        // Then
        assertThat(response.getBookId()).isNotNull();
        assertThat(response.getUniverseId()).isEqualTo(universe.getId());
        assertThat(response.getUniverseName()).isEqualTo("Cosmere");
        assertThat(response.getSeriesId()).isEqualTo(series.getId());
        assertThat(response.getSeriesName()).isEqualTo("Stormlight Archive");
        assertThat(response.getTitle()).isEqualTo("The Way of Kings");
        assertThat(response.getBookNumber()).isEqualTo(1);
        assertThat(response.isCreated()).isTrue();
    }

    @Test
    void shouldReturnExistingBookWhenTitleExistsInSameSeries() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        Series series = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        contentPersistencePort.createUniverse(universe);
        contentPersistencePort.createSeries(series);
        
        Book existingBook = Book.createInSeries(universe.getId(), "Cosmere", series.getId(), "Stormlight Archive", 1, "The Way of Kings");
        contentPersistencePort.createBook(existingBook);
        
        CreateBookRequest request = CreateBookRequest.inSeries(universe.getId(), series.getId(), "The Way of Kings", 1);

        // When
        CreateBookResponse response = catalogService.createBook(request);

        // Then
        assertThat(response.getBookId()).isEqualTo(existingBook.getId());
        assertThat(response.isCreated()).isFalse();
    }

    @Test
    void shouldAllowSameBookTitleInDifferentSeries() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        Series stormlight = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        Series mistborn = Series.create(universe.getId(), "Cosmere", "Mistborn");
        contentPersistencePort.createUniverse(universe);
        contentPersistencePort.createSeries(stormlight);
        contentPersistencePort.createSeries(mistborn);
        
        Book stormlightBook = Book.createInSeries(universe.getId(), "Cosmere", stormlight.getId(), "Stormlight Archive", 1, "Foundation");
        contentPersistencePort.createBook(stormlightBook);
        
        CreateBookRequest request = CreateBookRequest.inSeries(universe.getId(), mistborn.getId(), "Foundation", 1);

        // When
        CreateBookResponse response = catalogService.createBook(request);

        // Then
        assertThat(response.getBookId()).isNotEqualTo(stormlightBook.getId());
        assertThat(response.getSeriesId()).isEqualTo(mistborn.getId());
        assertThat(response.isCreated()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenUniverseNotFoundForBook() {
        // Given
        UUID nonExistentUniverseId = UUID.randomUUID();
        CreateBookRequest request = CreateBookRequest.standalone(nonExistentUniverseId, "Some Book");

        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe not found")
                .hasMessageContaining(nonExistentUniverseId.toString());
    }

    @Test
    void shouldThrowExceptionWhenSeriesNotFound() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistencePort.createUniverse(universe);
        UUID nonExistentSeriesId = UUID.randomUUID();
        
        CreateBookRequest request = CreateBookRequest.inSeries(universe.getId(), nonExistentSeriesId, "Some Book", 1);

        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Series not found")
                .hasMessageContaining(nonExistentSeriesId.toString());
    }

    @Test
    void shouldThrowExceptionWhenSeriesDoesNotBelongToUniverse() {
        // Given
        Universe cosmere = Universe.ofName("Cosmere");
        Universe marvel = Universe.ofName("Marvel");
        contentPersistencePort.createUniverse(cosmere);
        contentPersistencePort.createUniverse(marvel);
        
        Series marvelSeries = Series.create(marvel.getId(), "Marvel", "X-Men");
        contentPersistencePort.createSeries(marvelSeries);
        
        CreateBookRequest request = CreateBookRequest.inSeries(cosmere.getId(), marvelSeries.getId(), "Some Book", 1);

        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Series does not belong to the specified universe");
    }

    @DisplayName("Validation")
    @Test
    void shouldThrowExceptionWhenSeriesNameIsBlank() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistencePort.createUniverse(universe);
        
        CreateSeriesRequest request = new CreateSeriesRequest(universe.getId(), "   ");

        // When & Then
        assertThatThrownBy(() -> catalogService.createSeries(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Series name cannot be null or blank");
    }

    @Test
    void shouldThrowExceptionWhenBookTitleIsBlank() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistencePort.createUniverse(universe);
        
        CreateBookRequest request = CreateBookRequest.standalone(universe.getId(), "   ");

        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Book title cannot be null or blank");
    }
}
