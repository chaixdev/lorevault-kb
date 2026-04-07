package com.lorevault.api.infrastructure.persistence.neo4j;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.BookGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SeriesGraphRepository;
// no direct use of UniverseGraphRepository in this test
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
// import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused integration test for Neo4j relationship verification.
 * Uses only the data layer without loading the full application context.
 */
@DataNeo4jTest
@Testcontainers
@Import({Neo4jContentPersistenceAdapter.class})
public class Neo4jRelationshipIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
        .withAdminPassword("testpass123");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", neo4jContainer::getAdminPassword);
    }

    @Autowired
    private Neo4jContentPersistenceAdapter adapter;

    // Repositories for assertions

    @Autowired
    private SeriesGraphRepository seriesRepository;

    @Autowired
    private BookGraphRepository bookRepository;

    // Neo4jTemplate not used directly; remove to avoid unused field warning

    @Test
    void shouldCreateProperRelationshipsForSeriesInUniverse() {
        // Given
        Universe universe = Universe.ofName("Test Universe");
        Series series = Series.create(universe.getId(), universe.getName(), "Test Series");

        // When
    Universe savedUniverse = adapter.createUniverse(universe);
        Series savedSeries = adapter.createSeries(series);

        // Then
        Optional<Series> seriesNode = seriesRepository.findById(savedSeries.getId());
        assertThat(seriesNode).isPresent();
        assertThat(seriesNode.get().getUniverseId()).isEqualTo(savedUniverse.getId());
    // Sanity
    assertThat(savedUniverse.getId()).isNotNull();
    }

    @Test
    void shouldCreateProperRelationshipsForBookInSeries() {
        // Given
        Universe universe = Universe.ofName("Test Universe");
        Series series = Series.create(universe.getId(), universe.getName(), "Test Series");
        Book book = Book.createInSeries(universe.getId(), universe.getName(), series.getId(), series.getName(), 1, "Test Book");

        // When
        Universe savedUniverse = adapter.createUniverse(universe);
        Series savedSeries = adapter.createSeries(series);
        Book savedBook = adapter.createBook(book);

        // Then
        Optional<Book> bookNode = bookRepository.findById(savedBook.getId());
        assertThat(bookNode).isPresent();
        
        // Verify book -> series relationship
        assertThat(bookNode.get().getSeriesId()).isEqualTo(savedSeries.getId());
        assertThat(bookNode.get().getUniverseId()).isEqualTo(savedUniverse.getId());
    }

    @Test
    void shouldCreateStandaloneBookWithoutRelationships() {
        // Given
        Universe universe = Universe.ofName("Test Universe");
        Book book = Book.createStandalone(universe.getId(), universe.getName(), "Standalone Book");

        // When
        adapter.createUniverse(universe);
        Book savedBook = adapter.createBook(book);

        // Then
        Optional<Book> bookNode = bookRepository.findById(savedBook.getId());
        assertThat(bookNode).isPresent();
        assertThat(bookNode.get().getSeriesId()).isNull();
    }
}
