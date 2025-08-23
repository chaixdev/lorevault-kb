package com.lorevault.api.infrastructure.persistence.neo4j;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jMapper;
import com.lorevault.api.infrastructure.persistence.neo4j.model.BookNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SeriesNode;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.BookGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SeriesGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.UniverseGraphRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused integration test for Neo4j relationship verification.
 * Uses only the data layer without loading the full application context.
 */
@DataNeo4jTest
@Testcontainers
@Import({Neo4jContentPersistenceAdapter.class, Neo4jMapper.class})
public class Neo4jRelationshipIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.20")
            .withAdminPassword("testpass123");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", neo4jContainer::getAdminPassword);
    }

    @Autowired
    private Neo4jContentPersistenceAdapter adapter;

    @Autowired
    private UniverseGraphRepository universeRepository;

    @Autowired
    private SeriesGraphRepository seriesRepository;

    @Autowired
    private BookGraphRepository bookRepository;

    @Autowired
    private Neo4jTemplate neo4jTemplate;

    @Test
    void shouldCreateProperRelationshipsForSeriesInUniverse() {
        // Given
        Universe universe = Universe.ofName("Test Universe");
        Series series = Series.create(universe.getId(), universe.getName(), "Test Series");

        // When
        Universe savedUniverse = adapter.createUniverse(universe);
        Series savedSeries = adapter.createSeries(series);

        // Then
        Optional<SeriesNode> seriesNode = seriesRepository.findById(savedSeries.getId());
        assertThat(seriesNode).isPresent();
        assertThat(seriesNode.get().getUniverse()).isNotNull();
        assertThat(seriesNode.get().getUniverse().getId()).isEqualTo(savedUniverse.getId());
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
        Optional<BookNode> bookNode = bookRepository.findById(savedBook.getId());
        assertThat(bookNode).isPresent();
        
        // Verify book -> series relationship
        assertThat(bookNode.get().getSeriesNode()).isNotNull();
        assertThat(bookNode.get().getSeriesNode().getId()).isEqualTo(savedSeries.getId());
        
        // Verify book -> universe relationship (through series)
        assertThat(bookNode.get().getSeriesNode().getUniverse()).isNotNull();
        assertThat(bookNode.get().getSeriesNode().getUniverse().getId()).isEqualTo(savedUniverse.getId());
    }

    @Test
    void shouldCreateStandaloneBookWithoutRelationships() {
        // Given
        Universe universe = Universe.ofName("Test Universe");
        Book book = Book.createStandalone(universe.getId(), universe.getName(), "Standalone Book");

        // When
        Universe savedUniverse = adapter.createUniverse(universe);
        Book savedBook = adapter.createBook(book);

        // Then
        Optional<BookNode> bookNode = bookRepository.findById(savedBook.getId());
        assertThat(bookNode).isPresent();
        assertThat(bookNode.get().getSeriesNode()).isNull();
    }
}