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

import com.lorevault.api.content.Book;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.PublicationCoordinates;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.testing.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class ChapterPublicationPersistenceIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpassword")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
    }

    @Autowired
    private BookGraphRepository bookRepo;

    @Autowired
    private ChapterGraphRepository chapterRepo;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void setUp() {
        chapterRepo.deleteAll();
        bookRepo.deleteAll();
    }

    @Test
    void saveChapterBuiltWithFactory_shouldPersistPublicationScalarFields() {
        Book book = persistBook();

        Chapter chapter = buildFactoryChapter(book);

        Chapter saved = chapterRepo.save(chapter);

        Map<String, Object> persisted = loadChapterProperties(saved.getId());

        assertThat(persisted.get("chapterTitle")).isEqualTo("The Kevin Jenkins Experience");
        assertThat(persisted.get("universe")).isEqualTo("JENKINSVERSE");
        assertThat(persisted.get("series")).isEqualTo("Deathworlders");
        assertThat(persisted.get("bookTitle")).isEqualTo("The Deathworlders");
        assertThat(persisted.get("bookNumber")).isEqualTo(0L);
        assertThat(persisted.get("chapterNumber")).isEqualTo(1L);
    }

    @Test
    void saveChapterWithBookRelationshipAttached_shouldPersistPublicationScalarFields() {
        Book book = persistBook();

        Chapter chapter = buildFactoryChapter(book);
        chapter.setBook(book);

        Chapter saved = chapterRepo.save(chapter);

        Map<String, Object> persisted = loadChapterProperties(saved.getId());

        assertThat(persisted.get("chapterTitle")).isEqualTo("The Kevin Jenkins Experience");
        assertThat(persisted.get("universe")).isEqualTo("JENKINSVERSE");
        assertThat(persisted.get("series")).isEqualTo("Deathworlders");
        assertThat(persisted.get("bookTitle")).isEqualTo("The Deathworlders");
        assertThat(persisted.get("bookNumber")).isEqualTo(0L);
        assertThat(persisted.get("chapterNumber")).isEqualTo(1L);
    }

    @Test
    void saveChapterWithManualScalarPopulation_shouldPersistPublicationScalarFields() {
        Book book = persistBook();

        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setBookId(book.getId());
        chapter.setUniverseId(book.getUniverseId());
        chapter.setSeriesId(book.getSeriesId());
        chapter.setUniverse(book.getUniverse());
        chapter.setSeries(book.getSeries());
        chapter.setBookTitle(book.getTitle());
        chapter.setBookNumber(book.getBookNumber());
        chapter.setChapterNumber(1);
        chapter.setChapterTitle("The Kevin Jenkins Experience");
        chapter.setRawText("Kevin Jenkins wakes up on an alien world.");
        chapter.setContentHash("manual-hash");
        chapter.setBook(book);

        Chapter saved = chapterRepo.save(chapter);

        Map<String, Object> persisted = loadChapterProperties(saved.getId());

        assertThat(persisted.get("chapterTitle")).isEqualTo("The Kevin Jenkins Experience");
        assertThat(persisted.get("universe")).isEqualTo("JENKINSVERSE");
        assertThat(persisted.get("series")).isEqualTo("Deathworlders");
        assertThat(persisted.get("bookTitle")).isEqualTo("The Deathworlders");
        assertThat(persisted.get("bookNumber")).isEqualTo(0L);
        assertThat(persisted.get("chapterNumber")).isEqualTo(1L);
    }

    private Book persistBook() {
        Book book = Book.createInSeries(
                UUID.randomUUID(),
                "JENKINSVERSE",
                UUID.randomUUID(),
                "Deathworlders",
                0,
                "The Deathworlders");
        return bookRepo.save(book);
    }

    private Chapter buildFactoryChapter(Book book) {
        PublicationCoordinates coordinates = new PublicationCoordinates(
                book.getUniverse(),
                book.getSeries(),
                book.getTitle(),
                "The Kevin Jenkins Experience",
                book.getBookNumber(),
                1);

        return Chapter.createWithReferences(
                book.getId(),
                book.getUniverseId(),
                book.getSeriesId(),
                coordinates,
                "The Kevin Jenkins Experience",
                "Kevin Jenkins wakes up on an alien world.",
                "factory-hash");
    }

    private Map<String, Object> loadChapterProperties(UUID chapterId) {
        Optional<Map<String, Object>> row = neo4jClient.query("""
                MATCH (c:Chapter {id: $chapterId})
                RETURN c.chapterTitle AS chapterTitle,
                       c.universe AS universe,
                       c.series AS series,
                       c.bookTitle AS bookTitle,
                       c.bookNumber AS bookNumber,
                       c.chapterNumber AS chapterNumber
                """)
                .bind(chapterId).to("chapterId")
                .fetch()
                .one();

        assertThat(row).isPresent();
        return row.orElseThrow();
    }
}
