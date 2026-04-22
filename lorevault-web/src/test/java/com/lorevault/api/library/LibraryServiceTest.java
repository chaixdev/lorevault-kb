package com.lorevault.api.library;

import com.lorevault.api.content.domain.Universe;
import com.lorevault.api.content.domain.Series;
import com.lorevault.api.content.domain.Book;
import com.lorevault.api.testutil.fakes.FakeContentRepositories;
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

    private FakeContentRepositories contentPersistence;
    private LibraryService catalogService;

    @BeforeEach
    void setUp() {
        contentPersistence = new FakeContentRepositories();
        catalogService = new LibraryService(contentPersistence.asUniverseRepo(), contentPersistence.asSeriesRepo(), contentPersistence.asBookRepo());
    }

    @DisplayName("Universe Creation")
    @Test
    void shouldCreateNewUniverse() {
        // When
        LibraryResult<Universe> result = catalogService.createUniverse("Cosmere");
        Universe universe = result.entity();

        // Then
        assertThat(universe.getId()).isNotNull();
        assertThat(universe.getName()).isEqualTo("Cosmere");
        assertThat(universe.getSlug()).isEqualTo("cosmere");
        assertThat(result.isNew()).isTrue();
        assertThat(universe.getCreatedAt()).isNotNull();
        assertThat(universe.getUpdatedAt()).isNotNull();
        
        // Verify stored in persistence
        assertThat(contentPersistence.findUniverseById(universe.getId())).isPresent();
    }

    @Test
    void shouldReturnExistingUniverseWhenNameAlreadyExists() {
        // Given
        Universe existingUniverse = Universe.ofName("Cosmere");
        contentPersistence.createUniverse(existingUniverse);
        
        // When
        LibraryResult<Universe> result = catalogService.createUniverse("Cosmere");
        Universe universe = result.entity();

        // Then
        assertThat(universe.getId()).isEqualTo(existingUniverse.getId());
        assertThat(universe.getName()).isEqualTo("Cosmere");
        assertThat(universe.getSlug()).isEqualTo("cosmere");
        assertThat(result.isNew()).isFalse();
        assertThat(universe.getCreatedAt()).isEqualTo(existingUniverse.getCreatedAt());
        assertThat(universe.getUpdatedAt()).isEqualTo(existingUniverse.getUpdatedAt());
    }

    @Test
    void shouldThrowExceptionWhenUniverseNameIsNull() {
        // When & Then
        assertThatThrownBy(() -> catalogService.createUniverse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe name cannot be null or blank");
    }

    @Test
    void shouldThrowExceptionWhenUniverseNameIsBlank() {
        // When & Then
        assertThatThrownBy(() -> catalogService.createUniverse("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe name cannot be null or blank");
    }

    @DisplayName("Series Creation")
    @Test
    void shouldCreateNewSeriesInUniverse() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistence.createUniverse(universe);
        
        // When
        LibraryResult<Series> result = catalogService.createSeries(universe.getId(), "Stormlight Archive");
        Series series = result.entity();

        // Then
        assertThat(series.getId()).isNotNull();
        assertThat(series.getUniverseId()).isEqualTo(universe.getId());
        assertThat(series.getUniverseName()).isEqualTo("Cosmere");
        assertThat(series.getName()).isEqualTo("Stormlight Archive");
        assertThat(result.isNew()).isTrue();
        assertThat(series.getCreatedAt()).isNotNull();
        assertThat(series.getUpdatedAt()).isNotNull();
        
        // Verify stored in persistence
        assertThat(contentPersistence.findSeriesById(series.getId())).isPresent();
    }

    @Test
    void shouldReturnExistingSeriesWhenNameAlreadyExistsInUniverse() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistence.createUniverse(universe);
        
        Series existingSeries = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        contentPersistence.createSeries(existingSeries);
        
        // When
        LibraryResult<Series> result = catalogService.createSeries(universe.getId(), "Stormlight Archive");
        Series series = result.entity();

        // Then
        assertThat(series.getId()).isEqualTo(existingSeries.getId());
        assertThat(series.getUniverseId()).isEqualTo(universe.getId());
        assertThat(series.getName()).isEqualTo("Stormlight Archive");
        assertThat(result.isNew()).isFalse();
    }

    @Test
    void shouldAllowSameSeriesNameInDifferentUniverses() {
        // Given
        Universe cosmere = Universe.ofName("Cosmere");
        Universe marvel = Universe.ofName("Marvel");
        contentPersistence.createUniverse(cosmere);
        contentPersistence.createUniverse(marvel);
        
        Series cosmereSeries = Series.create(cosmere.getId(), "Cosmere", "Foundation");
        contentPersistence.createSeries(cosmereSeries);
        
        // When
        LibraryResult<Series> result = catalogService.createSeries(marvel.getId(), "Foundation");
        Series series = result.entity();

        // Then
        assertThat(series.getId()).isNotEqualTo(cosmereSeries.getId());
        assertThat(series.getUniverseId()).isEqualTo(marvel.getId());
        assertThat(series.getName()).isEqualTo("Foundation");
        assertThat(result.isNew()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenUniverseNotFound() {
        // Given
        UUID nonExistentUniverseId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> catalogService.createSeries(nonExistentUniverseId, "Stormlight Archive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe not found")
                .hasMessageContaining(nonExistentUniverseId.toString());
    }

    @DisplayName("Book Creation")
    @Test
    void shouldCreateStandaloneBookInUniverse() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistence.createUniverse(universe);
        
        // When
        LibraryResult<Book> result = catalogService.createBook(universe.getId(), null, "Warbreaker", null);
        Book book = result.entity();

        // Then
        assertThat(book.getId()).isNotNull();
        assertThat(book.getUniverseId()).isEqualTo(universe.getId());
        assertThat(book.getUniverse()).isEqualTo("Cosmere");
        assertThat(book.getSeriesId()).isNull();
        assertThat(book.getSeries()).isNull();
        assertThat(book.getTitle()).isEqualTo("Warbreaker");
        assertThat(book.getBookNumber()).isNull();
        assertThat(result.isNew()).isTrue();
        
        // Verify stored in persistence
        assertThat(contentPersistence.findBookById(book.getId())).isPresent();
    }

    @Test
    void shouldCreateBookInSeries() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        Series series = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        contentPersistence.createUniverse(universe);
        contentPersistence.createSeries(series);
        
        // When
        LibraryResult<Book> result = catalogService.createBook(universe.getId(), series.getId(), "The Way of Kings", 1);
        Book book = result.entity();

        // Then
        assertThat(book.getId()).isNotNull();
        assertThat(book.getUniverseId()).isEqualTo(universe.getId());
        assertThat(book.getUniverse()).isEqualTo("Cosmere");
        assertThat(book.getSeriesId()).isEqualTo(series.getId());
        assertThat(book.getSeries()).isEqualTo("Stormlight Archive");
        assertThat(book.getTitle()).isEqualTo("The Way of Kings");
        assertThat(book.getBookNumber()).isEqualTo(1);
        assertThat(result.isNew()).isTrue();
    }

    @Test
    void shouldReturnExistingBookWhenTitleExistsInSameSeries() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        Series series = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        contentPersistence.createUniverse(universe);
        contentPersistence.createSeries(series);
        
        Book existingBook = Book.createInSeries(universe.getId(), "Cosmere", series.getId(), "Stormlight Archive", 1, "The Way of Kings");
        contentPersistence.createBook(existingBook);
        
        // When
        LibraryResult<Book> result = catalogService.createBook(universe.getId(), series.getId(), "The Way of Kings", 1);
        Book book = result.entity();

        // Then
        assertThat(book.getId()).isEqualTo(existingBook.getId());
        assertThat(result.isNew()).isFalse();
    }

    @Test
    void shouldAllowSameBookTitleInDifferentSeries() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        Series stormlight = Series.create(universe.getId(), "Cosmere", "Stormlight Archive");
        Series mistborn = Series.create(universe.getId(), "Cosmere", "Mistborn");
        contentPersistence.createUniverse(universe);
        contentPersistence.createSeries(stormlight);
        contentPersistence.createSeries(mistborn);
        
        Book stormlightBook = Book.createInSeries(universe.getId(), "Cosmere", stormlight.getId(), "Stormlight Archive", 1, "Foundation");
        contentPersistence.createBook(stormlightBook);
        
        // When
        LibraryResult<Book> result = catalogService.createBook(universe.getId(), mistborn.getId(), "Foundation", 1);
        Book book = result.entity();

        // Then
        assertThat(book.getId()).isNotEqualTo(stormlightBook.getId());
        assertThat(book.getSeriesId()).isEqualTo(mistborn.getId());
        assertThat(result.isNew()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenUniverseNotFoundForBook() {
        // Given
        UUID nonExistentUniverseId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(nonExistentUniverseId, null, "Some Book", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Universe not found")
                .hasMessageContaining(nonExistentUniverseId.toString());
    }

    @Test
    void shouldThrowExceptionWhenSeriesNotFound() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistence.createUniverse(universe);
        UUID nonExistentSeriesId = UUID.randomUUID();
        
        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(universe.getId(), nonExistentSeriesId, "Some Book", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Series not found")
                .hasMessageContaining(nonExistentSeriesId.toString());
    }

    @Test
    void shouldThrowExceptionWhenSeriesDoesNotBelongToUniverse() {
        // Given
        Universe cosmere = Universe.ofName("Cosmere");
        Universe marvel = Universe.ofName("Marvel");
        contentPersistence.createUniverse(cosmere);
        contentPersistence.createUniverse(marvel);
        
        Series marvelSeries = Series.create(marvel.getId(), "Marvel", "X-Men");
        contentPersistence.createSeries(marvelSeries);
        
        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(cosmere.getId(), marvelSeries.getId(), "Some Book", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Series does not belong to the specified universe");
    }

    @DisplayName("Validation")
    @Test
    void shouldThrowExceptionWhenSeriesNameIsBlank() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistence.createUniverse(universe);
        
        // When & Then
        assertThatThrownBy(() -> catalogService.createSeries(universe.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Series name cannot be null or blank");
    }

    @Test
    void shouldThrowExceptionWhenBookTitleIsBlank() {
        // Given
        Universe universe = Universe.ofName("Cosmere");
        contentPersistence.createUniverse(universe);
        
        // When & Then
        assertThatThrownBy(() -> catalogService.createBook(universe.getId(), null, "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Book title cannot be null or blank");
    }
}
