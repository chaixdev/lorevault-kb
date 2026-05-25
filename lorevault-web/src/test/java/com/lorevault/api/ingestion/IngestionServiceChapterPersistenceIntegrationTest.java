package com.lorevault.api.ingestion;
import com.lorevault.api.library.book.PublicationCoordinates;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.submission.IngestionService;

import com.lorevault.api.library.book.Book;
import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.ingestion.submission.IngestionSubmissionResult;
import com.lorevault.api.library.book.BookGraphRepository;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.testing.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
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
@Import({IngestionService.class, IngestionJobService.class})
@ActiveProfiles("test")
@Tag("integration")
class IngestionServiceChapterPersistenceIntegrationTest {

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
    private IngestionService ingestionService;

    @Autowired
    private IngestionJobService ingestionJobService;

    @Autowired
    private BookGraphRepository bookRepo;

    @Autowired
    private ChapterGraphRepository chapterRepo;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void setUp() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void submitChapter_shouldPersistPublicationScalarsOnCreatedChapter() {
        Book book = persistBook();

        IngestionSubmissionResult response = ingestionService.submitChapter(
                book.getId(), 1, "The Kevin Jenkins Experience", "Kevin Jenkins wakes up on an alien world.");

        assertThat(response).isNotNull();
        assertThat(response.chapterId()).isNotNull();
        assertThat(response.jobId()).isNotNull();

        Map<String, Object> persisted = loadChapterProperties(response.chapterId());

        assertThat(persisted.get("chapterTitle")).isEqualTo("The Kevin Jenkins Experience");
        assertThat(persisted.get("universe")).isEqualTo("JENKINSVERSE");
        assertThat(persisted.get("series")).isEqualTo("Deathworlders");
        assertThat(persisted.get("bookTitle")).isEqualTo("The Deathworlders");
        assertThat(persisted.get("bookNumber")).isEqualTo(0L);
        assertThat(persisted.get("chapterNumber")).isEqualTo(1L);

    }

    @Test
    void createIngestionJob_shouldNotMutateExistingChapterPublicationScalars() {
        Book book = persistBook();
        Chapter chapter = persistChapter(book);

        Map<String, Object> before = loadChapterProperties(chapter.getId());

        var job = ingestionJobService.createIngestionJob(chapter.getId());

        assertThat(job).isNotNull();
        assertThat(job.getId()).isNotNull();

        Map<String, Object> after = loadChapterProperties(chapter.getId());

        assertThat(after).isEqualTo(before);
        assertThat(after.get("universe")).isEqualTo("JENKINSVERSE");
        assertThat(after.get("series")).isEqualTo("Deathworlders");
        assertThat(after.get("bookTitle")).isEqualTo("The Deathworlders");
        assertThat(after.get("bookNumber")).isEqualTo(0L);
        assertThat(after.get("chapterNumber")).isEqualTo(1L);
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

    private Chapter persistChapter(Book book) {
        Chapter chapter = chapterRepo.save(Chapter.createWithReferences(
                book.getId(),
                book.getUniverseId(),
                book.getSeriesId(),
                new PublicationCoordinates(
                        book.getUniverse(),
                        book.getSeries(),
                        book.getTitle(),
                        "The Kevin Jenkins Experience",
                        book.getBookNumber(),
                        1),
                "The Kevin Jenkins Experience",
                "Kevin Jenkins wakes up on an alien world.",
                "ingestion-service-test-hash"
        ));
        chapter.setBook(book);
        return chapterRepo.save(chapter);
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
                .bind(chapterId.toString()).to("chapterId")
                .fetch()
                .one();

        assertThat(row).isPresent();
        return row.orElseThrow();
    }
}
