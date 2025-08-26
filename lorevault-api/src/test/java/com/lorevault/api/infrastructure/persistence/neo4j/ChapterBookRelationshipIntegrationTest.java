package com.lorevault.api.infrastructure.persistence.neo4j;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jMapper;
import com.lorevault.api.infrastructure.persistence.neo4j.model.BookNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.BookGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterGraphRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@Import({Neo4jContentPersistenceAdapter.class, Neo4jMapper.class})
class ChapterBookRelationshipIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpass123");

    @DynamicPropertySource
    static void neo4jProps(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", neo4jContainer::getAdminPassword);
    }

    @Autowired
    private Neo4jContentPersistenceAdapter adapter;

    @Autowired
    private BookGraphRepository bookRepo;

    @Autowired
    private ChapterGraphRepository chapterRepo;

    @Test
    void shouldLinkChapterToBook() {
        // Given: universe and a standalone book
        Universe universe = Universe.ofName("Test Universe");
        var savedUniverse = adapter.createUniverse(universe);
        Book book = Book.createStandalone(savedUniverse.getId(), savedUniverse.getName(), "Standalone");
        Book savedBook = adapter.createBook(book);

        // And: a chapter referencing that bookId
        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse(savedBook.getUniverse());
        coords.setBookTitle(savedBook.getTitle());
        coords.setBookNumber(savedBook.getBookNumber() != null ? savedBook.getBookNumber() : 0);
        coords.setChapterTitle("Ch 1");
        coords.setChapterNumber(1);

        Chapter chapter = Chapter.createStandalone(savedBook.getId(), savedUniverse.getId(), coords, "Ch 1", "text", "hash-1");

        // When: persist chapter
        Chapter persisted = adapter.createChapter(chapter);

        // Then: Chapter node should have an outgoing IN_BOOK to the Book node
        Optional<ChapterNode> chapterNode = chapterRepo.findById(persisted.getId());
        Optional<BookNode> bookNode = bookRepo.findById(savedBook.getId());

        assertThat(chapterNode).isPresent();
        assertThat(bookNode).isPresent();
        assertThat(chapterNode.get().getBook()).isNotNull();
        assertThat(chapterNode.get().getBook().getId()).isEqualTo(savedBook.getId());
    }
}
