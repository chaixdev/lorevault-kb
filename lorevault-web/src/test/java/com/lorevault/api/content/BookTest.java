package com.lorevault.api.content;
import com.lorevault.api.library.domain.Book;

import com.lorevault.api.testutil.TestIds;
import com.lorevault.api.testutil.builders.BookBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Book")
class BookTest {

    @Test
    @DisplayName("should create book with all required fields")
    void shouldCreateBookWithAllRequiredFields() {
        // Given
        UUID id = TestIds.BOOK_ID;
        UUID universeId = TestIds.UNIVERSE_ID;
        UUID seriesId = TestIds.SERIES_ID;
        String universe = TestIds.DEFAULT_UNIVERSE_NAME;
        String series = "Stormlight Archive";
        Integer bookNumber = 1;
        String title = "The Way of Kings";
        LocalDateTime ts = TestIds.FIXED_TIMESTAMP;

        // When
        Book book = BookBuilder.aBook()
                .withId(id)
                .withUniverseId(universeId)
                .withSeriesId(seriesId)
                .withUniverse(universe)
                .withSeries(series)
                .withBookNumber(bookNumber)
                .withTitle(title)
                .withCreatedAt(ts)
                .withUpdatedAt(ts)
                .build();

        // Then
        assertThat(book.getId()).isEqualTo(id);
        assertThat(book.getUniverseId()).isEqualTo(universeId);
        assertThat(book.getSeriesId()).isEqualTo(seriesId);
        assertThat(book.getUniverse()).isEqualTo(universe);
        assertThat(book.getSeries()).isEqualTo(series);
        assertThat(book.getBookNumber()).isEqualTo(bookNumber);
        assertThat(book.getTitle()).isEqualTo(title);
        assertThat(book.getCreatedAt()).isEqualTo(ts);
        assertThat(book.getUpdatedAt()).isEqualTo(ts);
    }

    @Test
    @DisplayName("should create series book via factory method")
    void shouldCreateSeriesBookViaFactory() {
        // Given
        UUID universeId = TestIds.UNIVERSE_ID;
        UUID seriesId = TestIds.SERIES_ID;
        String universe = TestIds.DEFAULT_UNIVERSE_NAME;
        String series = "Mistborn";
        Integer bookNumber = 2;
        String title = "The Well of Ascension";

        // When
        Book book = Book.createInSeries(universeId, universe, seriesId, series, bookNumber, title);

        // Then
        assertThat(book.getId()).isNotNull();
        assertThat(book.getUniverseId()).isEqualTo(universeId);
        assertThat(book.getSeriesId()).isEqualTo(seriesId);
        assertThat(book.getUniverse()).isEqualTo(universe);
        assertThat(book.getSeries()).isEqualTo(series);
        assertThat(book.getBookNumber()).isEqualTo(bookNumber);
        assertThat(book.getTitle()).isEqualTo(title);
        assertThat(book.getUpdatedAt()).isEqualTo(book.getCreatedAt());
    }

    @Test
    @DisplayName("should create standalone book via factory method")
    void shouldCreateStandaloneBookViaFactory() {
        // Given
        UUID universeId = TestIds.UNIVERSE_ID;
        String universe = TestIds.DEFAULT_UNIVERSE_NAME;
        String title = "Elantris";

        // When
        Book book = Book.createStandalone(universeId, universe, title);

        // Then
        assertThat(book.getId()).isNotNull();
        assertThat(book.getUniverseId()).isEqualTo(universeId);
        assertThat(book.getSeriesId()).isNull();
        assertThat(book.getUniverse()).isEqualTo(universe);
        assertThat(book.getSeries()).isNull();
        assertThat(book.getBookNumber()).isNull();
        assertThat(book.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("should format display label correctly")
    void shouldFormatDisplayLabelCorrectly() {
        Book book = BookBuilder.aBook()
                .withSeries("Stormlight Archive")
                .withBookNumber(4)
                .withTitle("Rhythm of War")
                .build();

        assertThat(book.displayLabel()).isEqualTo("Stormlight Archive #4 Rhythm of War");

        // Standalone
        Book standalone = BookBuilder.aStandaloneBook()
                .withTitle("Elantris")
                .build();
        assertThat(standalone.displayLabel()).isEqualTo("Elantris");
    }
}
