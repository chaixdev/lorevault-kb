package com.lorevault.api.infrastructure.persistence.neo4j;

import com.lorevault.api.application.port.ContentPersistencePort;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify that Neo4j relationships are properly created
 * for the publication hierarchy (Universe -> Series -> Book).
 *
 * Uses a focused DataNeo4j slice with a Testcontainers Neo4j instance to avoid
 * booting the full application context and unrelated beans.
 */
@DataNeo4jTest
@Testcontainers
@Import({Neo4jContentPersistenceAdapter.class, Neo4jMapper.class})
@Transactional
class LibraryHierarchyRelationshipIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.20")
            .withAdminPassword("testpass123");

    @DynamicPropertySource
    static void neo4jProps(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", neo4jContainer::getAdminPassword);
    }

    @Autowired
    private ContentPersistencePort contentPersistencePort;
    
    @Autowired
    private UniverseGraphRepository universeRepo;
    
    @Autowired
    private SeriesGraphRepository seriesRepo;
    
    @Autowired
    private BookGraphRepository bookRepo;

    @Test
    void shouldCreateProperRelationshipsForSeriesInUniverse() {
        // Given: Create a universe
        Universe universe = Universe.ofName("Test Universe");
        Universe savedUniverse = contentPersistencePort.createUniverse(universe);
        
        // When: Create a series in that universe
        Series series = Series.create(savedUniverse.getId(), savedUniverse.getName(), "Test Series");
        Series savedSeries = contentPersistencePort.createSeries(series);
        
        // Then: Verify the series node has proper relationship to universe
        Optional<SeriesNode> seriesNode = seriesRepo.findById(savedSeries.getId());
        assertThat(seriesNode).isPresent();
        assertThat(seriesNode.get().getUniverse()).isNotNull();
        assertThat(seriesNode.get().getUniverse().getId()).isEqualTo(savedUniverse.getId());
        assertThat(seriesNode.get().getUniverseId()).isEqualTo(savedUniverse.getId());
    }

    @Test
    void shouldCreateProperRelationshipsForBookInSeries() {
        // Given: Create universe and series
        Universe universe = Universe.ofName("Test Universe");
        Universe savedUniverse = contentPersistencePort.createUniverse(universe);
        
        Series series = Series.create(savedUniverse.getId(), savedUniverse.getName(), "Test Series");
        Series savedSeries = contentPersistencePort.createSeries(series);
        
        // When: Create a book in that series
        Book book = Book.createInSeries(
            savedUniverse.getId(), 
            savedUniverse.getName(),
            savedSeries.getId(),
            savedSeries.getName(),
            1,
            "Test Book"
        );
        Book savedBook = contentPersistencePort.createBook(book);
        
        // Then: Verify the book node has proper relationships
        Optional<BookNode> bookNode = bookRepo.findById(savedBook.getId());
        assertThat(bookNode).isPresent();
        
        // Verify universe relationship
        assertThat(bookNode.get().getUniverseNode()).isNotNull();
        assertThat(bookNode.get().getUniverseNode().getId()).isEqualTo(savedUniverse.getId());
        assertThat(bookNode.get().getUniverseId()).isEqualTo(savedUniverse.getId());
        
        // Verify series relationship
        assertThat(bookNode.get().getSeriesNode()).isNotNull();
        assertThat(bookNode.get().getSeriesNode().getId()).isEqualTo(savedSeries.getId());
        assertThat(bookNode.get().getSeriesId()).isEqualTo(savedSeries.getId());
    }

    @Test
    void shouldCreateProperRelationshipsForStandaloneBook() {
        // Given: Create universe
        Universe universe = Universe.ofName("Test Universe");
        Universe savedUniverse = contentPersistencePort.createUniverse(universe);
        
        // When: Create a standalone book
        Book book = Book.createStandalone(
            savedUniverse.getId(), 
            savedUniverse.getName(),
            "Standalone Book"
        );
        Book savedBook = contentPersistencePort.createBook(book);
        
        // Then: Verify the book node has universe relationship but no series relationship
        Optional<BookNode> bookNode = bookRepo.findById(savedBook.getId());
        assertThat(bookNode).isPresent();
        
        // Verify universe relationship
        assertThat(bookNode.get().getUniverseNode()).isNotNull();
        assertThat(bookNode.get().getUniverseNode().getId()).isEqualTo(savedUniverse.getId());
        assertThat(bookNode.get().getUniverseId()).isEqualTo(savedUniverse.getId());
        
        // Verify no series relationship
        assertThat(bookNode.get().getSeriesNode()).isNull();
        assertThat(bookNode.get().getSeriesId()).isNull();
    }
}