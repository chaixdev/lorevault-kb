package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
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

import com.lorevault.api.ai.application.SceneDetectionService;
import com.lorevault.api.ai.application.SceneProcessingService;
import com.lorevault.api.ai.domain.SceneWithCoordinates;
import com.lorevault.api.ai.application.TriadOrchestrationService;
import com.lorevault.api.content.Book;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.ingestion.events.ChapterIngestionEvent;
import com.lorevault.api.integration.TestConfig;
import com.lorevault.api.web.command.ingestion.BookIndividualResolutionResponse;
import com.lorevault.api.web.command.ingestion.ChapterIndividualResolutionResponse;
import com.lorevault.api.web.command.ingestion.SubmitChapterRequest;
import com.lorevault.api.web.command.ingestion.SubmitChapterResponse;
import com.lorevault.api.testutil.SampleChapterLoader;
import com.lorevault.api.testing.TestImages;
import com.lorevault.api.timeline.application.DefaultTemporalEdgeService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@DataNeo4jTest
@Testcontainers
@Import({
        TestConfig.class,
        IngestionService.class,
        IngestionJobService.class,
        SceneDetectionHandler.class,
        SceneProcessingService.class,
        IndividualPersistenceService.class,
        EventPersistenceService.class,
        ChapterIndividualResolutionService.class,
        BookIndividualReductionService.class,
        DefaultTemporalEdgeService.class
})
@Tag("integration")
class IndividualResolutionIT {

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
        registry.add("lorevault.system.health.startup.enabled", () -> "false");
        registry.add("lorevault.llm.health.enabled", () -> "false");
        registry.add("lorevault.embedding.health.enabled", () -> "false");
    }

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private ChapterGraphRepository chapterRepo;

    @Autowired
    private BookGraphRepository bookRepo;

    @Autowired
    private SceneDetectionHandler sceneDetectionHandler;

    @Autowired
    private ChapterIndividualResolutionService chapterIndividualResolutionService;

    @Autowired
    private BookIndividualReductionService bookIndividualReductionService;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private SceneDetectionService sceneDetectionService;

    @MockitoBean
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private DefaultTemporalEdgeService defaultTemporalEdgeService;

    @BeforeEach
    void setUp() {
        reset(eventPublisher, defaultTemporalEdgeService, sceneDetectionService);
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        persistDeathworldersBook();
        doNothing().when(defaultTemporalEdgeService).createAllDefaults(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fullProcessingCycle_linksMentionsToSingleChapterIndividual_perNormalizedName() {
        SubmitChapterRequest request = SampleChapterLoader.loadSampleChapter("kevin_jenkins");
        when(sceneDetectionService.detectScenesInText(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(outcomeWithRepeatedNyx());

        IngestionSubmissionResult response = ingestionService.submitChapter(
                request.getBookId(), request.getChapterNumber(), request.getChapterTitle(), request.getChapterText());
        UUID jobId = response.jobId();
        UUID chapterId = response.chapterId();

        sceneDetectionHandler.handleChapterIngestion(new ChapterIngestionEvent(this, jobId, chapterId));

        ChapterIndividualResolutionResult resolutionResponse = chapterIndividualResolutionService.resolveChapter(chapterId);
        BookIndividualResolutionResult bookResolutionResponse = bookIndividualReductionService.resolveBook(
                chapterRepo.findById(chapterId).orElseThrow().getBookId()
        );

        assertThat(chapterRepo.findById(chapterId)).isPresent();
        assertThat(resolutionResponse.success()).isTrue();
        assertThat(resolutionResponse.rawIndividualsProcessed()).isEqualTo(3);
        assertThat(resolutionResponse.chapterIndividualsCreated()).isEqualTo(2);
        assertThat(bookResolutionResponse.success()).isTrue();
        assertThat(bookResolutionResponse.chapterIndividualsProcessed()).isEqualTo(2);
        assertThat(bookResolutionResponse.bookIndividualsCreated()).isEqualTo(2);
        assertThat(countNodes("IndividualMention")).isEqualTo(3L);
        assertThat(countNodes("ChapterIndividual")).isEqualTo(2L);
        assertThat(countNodes("BookIndividual")).isEqualTo(2L);
        assertThat(countMentionLinks()).isEqualTo(3L);
        assertThat(countBookLinks()).isEqualTo(2L);
        assertThat(countChapterIndividualsForName(chapterId, "nyx")).isEqualTo(1L);
        assertThat(countMentionRefsForName(chapterId, "nyx")).isEqualTo(2L);
        assertThat(countChapterIndividualsForName(chapterId, "orion")).isEqualTo(1L);
        assertThat(countBookIndividualsForName(chapterRepo.findById(chapterId).orElseThrow().getBookId(), "nyx")).isEqualTo(1L);
        assertThat(loadChapterIndividualProjection(chapterId, "nyx"))
                .containsEntry("displayName", "Nyx")
                .containsEntry("mentionCount", 2L);

    }

    @Test
    void bookResolution_linksAllChapterIndividuals_withSameNormalizedNameAcrossChapters() {
        SubmitChapterRequest chapterOne = createSubmitChapterRequest(1, "Chapter One", "Kevin Jenkins arrives.");
        SubmitChapterRequest chapterTwo = createSubmitChapterRequest(2, "Chapter Two", "Kevin Jenkins observes the station.");
        SubmitChapterRequest chapterThree = createSubmitChapterRequest(3, "Chapter Three", "Kevin Jenkins departs.");

        when(sceneDetectionService.detectScenesInText(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        outcomeWithSingleIndividual("Kevin Jenkins"),
                        outcomeWithSingleIndividual("Kevin Jenkins"),
                        outcomeWithSingleIndividual("Kevin Jenkins")
                );

        UUID chapterIdOne = ingestAndResolveChapter(chapterOne);
        UUID chapterIdTwo = ingestAndResolveChapter(chapterTwo);
        UUID chapterIdThree = ingestAndResolveChapter(chapterThree);
        UUID bookId = chapterRepo.findById(chapterIdOne).orElseThrow().getBookId();

        BookIndividualResolutionResult bookResolutionResponse = bookIndividualReductionService.resolveBook(bookId);

        assertThat(bookResolutionResponse.success()).isTrue();
        assertThat(bookResolutionResponse.chapterIndividualsProcessed()).isEqualTo(1);
        assertThat(bookResolutionResponse.bookIndividualsCreated()).isEqualTo(1);
        assertThat(countNodes("ChapterIndividual")).isEqualTo(3L);
        assertThat(countNodes("BookIndividual")).isEqualTo(1L);
        assertThat(countBookLinks()).isEqualTo(3L);
        assertThat(countBookIndividualsForName(bookId, "kevin jenkins")).isEqualTo(1L);
        assertThat(countChapterIndividualsLinkedToBookIndividual(bookId, "kevin jenkins")).isEqualTo(3L);
    }

    private void persistDeathworldersBook() {
        Book book = Book.createInSeries(
                UUID.nameUUIDFromBytes("deathworlders-universe".getBytes()),
                "JENKINSVERSE",
                UUID.nameUUIDFromBytes("deathworlders-series".getBytes()),
                "Deathworlders",
                0,
                "The Deathworlders"
        );
        book.setId(UUID.nameUUIDFromBytes("Deathworlders".getBytes()));
        bookRepo.save(book);
    }

    private UUID ingestAndResolveChapter(SubmitChapterRequest request) {
        IngestionSubmissionResult response = ingestionService.submitChapter(
                request.getBookId(), request.getChapterNumber(), request.getChapterTitle(), request.getChapterText());
        UUID jobId = response.jobId();
        UUID chapterId = response.chapterId();

        sceneDetectionHandler.handleChapterIngestion(new ChapterIngestionEvent(this, jobId, chapterId));
        ChapterIndividualResolutionResult resolutionResponse = chapterIndividualResolutionService.resolveChapter(chapterId);

        assertThat(resolutionResponse.success()).isTrue();
        return chapterId;
    }

    private SubmitChapterRequest createSubmitChapterRequest(int chapterNumber, String chapterTitle, String chapterText) {
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setBookId(UUID.nameUUIDFromBytes("Deathworlders".getBytes()));
        request.setChapterNumber(chapterNumber);
        request.setChapterTitle(chapterTitle);
        request.setChapterText(chapterText);
        return request;
    }

    private SceneDetectionService.SceneDetectionOutcome outcomeWithRepeatedNyx() {
        List<SceneWithCoordinates> scenes = List.of(
                new SceneWithCoordinates(0, 0, 120, "Kevin arrives"),
                new SceneWithCoordinates(1, 121, 260, "Nyx returns")
        );
        List<TriadOrchestrationService.TriadSceneIndividualExtraction> extractions = List.of(
                new TriadOrchestrationService.TriadSceneIndividualExtraction(0, List.of(
                        new TriadOrchestrationService.TriadIndividualExtraction(List.of("Nyx", "N."), "tall", "20s", "pilot"),
                        new TriadOrchestrationService.TriadIndividualExtraction(List.of("Orion"), "broad", "30s", "captain")
                )),
                new TriadOrchestrationService.TriadSceneIndividualExtraction(1, List.of(
                        new TriadOrchestrationService.TriadIndividualExtraction(List.of("Nyx"), "tall", "20s", "pilot again")
                ))
        );
        return new SceneDetectionService.SceneDetectionOutcome(scenes, List.of(), extractions, List.of());
    }

    private SceneDetectionService.SceneDetectionOutcome outcomeWithSingleIndividual(String displayName) {
        List<SceneWithCoordinates> scenes = List.of(
                new SceneWithCoordinates(0, 0, 120, displayName + " scene")
        );
        List<TriadOrchestrationService.TriadSceneIndividualExtraction> extractions = List.of(
                new TriadOrchestrationService.TriadSceneIndividualExtraction(0, List.of(
                        new TriadOrchestrationService.TriadIndividualExtraction(List.of(displayName), null, null, null)
                ))
        );
        return new SceneDetectionService.SceneDetectionOutcome(scenes, List.of(), extractions, List.of());
    }

    private long countNodes(String label) {
        return neo4jClient.query("MATCH (n:" + label + ") RETURN count(n) AS value")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private long countMentionLinks() {
        return neo4jClient.query("MATCH (:IndividualMention)-[r:REFERS_TO]->(:ChapterIndividual) RETURN count(r) AS value")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private long countBookLinks() {
        return neo4jClient.query("MATCH (:ChapterIndividual)-[r:REFERS_TO]->(:BookIndividual) RETURN count(r) AS value")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private long countChapterIndividualsForName(UUID chapterId, String normalizedName) {
        return neo4jClient.query("""
                MATCH (ci:ChapterIndividual {chapterId: $chapterId, normalizedName: $normalizedName})
                RETURN count(ci) AS value
                """)
                .bind(chapterId.toString()).to("chapterId")
                .bind(normalizedName).to("normalizedName")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private long countMentionRefsForName(UUID chapterId, String normalizedName) {
        return neo4jClient.query("""
                MATCH (:IndividualMention {chapterId: $chapterId, normalizedName: $normalizedName})-[:REFERS_TO]->(:ChapterIndividual {chapterId: $chapterId, normalizedName: $normalizedName})
                RETURN count(*) AS value
                """)
                .bind(chapterId.toString()).to("chapterId")
                .bind(normalizedName).to("normalizedName")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private long countBookIndividualsForName(UUID bookId, String normalizedName) {
        return neo4jClient.query("""
                MATCH (bi:BookIndividual {bookId: $bookId, normalizedName: $normalizedName})
                RETURN count(bi) AS value
                """)
                .bind(bookId.toString()).to("bookId")
                .bind(normalizedName).to("normalizedName")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private long countChapterIndividualsLinkedToBookIndividual(UUID bookId, String normalizedName) {
        return neo4jClient.query("""
                MATCH (c:Chapter)-[:IN_BOOK]->(:Book {id: $bookId})
                MATCH (c)-[:HAS_INDIVIDUAL]->(ci:ChapterIndividual {normalizedName: $normalizedName})-[:REFERS_TO]->(:BookIndividual {bookId: $bookId, normalizedName: $normalizedName})
                RETURN count(DISTINCT ci) AS value
                """)
                .bind(bookId.toString()).to("bookId")
                .bind(normalizedName).to("normalizedName")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private Map<String, Object> loadChapterIndividualProjection(UUID chapterId, String normalizedName) {
        Optional<Map<String, Object>> row = neo4jClient.query("""
                MATCH (ci:ChapterIndividual {chapterId: $chapterId, normalizedName: $normalizedName})
                RETURN ci.displayName AS displayName,
                       ci.mentionCount AS mentionCount
                """)
                .bind(chapterId.toString()).to("chapterId")
                .bind(normalizedName).to("normalizedName")
                .fetch()
                .one();

        assertThat(row).isPresent();
        return row.orElseThrow();
    }
}
