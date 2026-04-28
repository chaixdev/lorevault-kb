package com.lorevault.api.ingestion;

import com.lorevault.api.config.LoreVaultLlmLoggingProperties;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.infrastructure.LlmCallLoggingService;
import com.lorevault.api.ingestion.infrastructure.LlmCallRecordGraphRepository;
import com.lorevault.api.ingestion.infrastructure.StatusRecordGraphRepository;
import com.lorevault.api.testing.TestImages;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class LlmCallRecordPersistenceIntegrationTest {

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
    private LlmCallRecordGraphRepository llmCallRepo;

    @Autowired
    private IngestionJobGraphRepository jobRepo;

    @Autowired
    private StatusRecordGraphRepository statusRepo;

    private LlmCallLoggingService loggingService;

    @BeforeEach
    void setUp() {
        llmCallRepo.deleteAll();
        statusRepo.deleteAll();
        jobRepo.deleteAll();
        loggingService = new LlmCallLoggingService(
                new LoreVaultLlmLoggingProperties(true, true, 50, true),
                jobRepo,
                statusRepo,
                llmCallRepo
        );
    }

    @Test
    void persistLlmCallRecord_shouldSaveAndRetrieve() {
        UUID jobId = createJob();

        loggingService.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "You are an AI assistant that detects scenes in narrative text...",
                "Chapter 1: The hero begins their journey in a small village...",
                "This is a test response that is longer than 50 characters and should be persisted fully in the integration test",
                1250L,
                500,
                150
        );

        List<LlmCallRecord> retrieved = llmCallRepo.findByJobId(jobId);
        assertThat(retrieved).hasSize(1);

        LlmCallRecord record = retrieved.getFirst();
        assertThat(record.getJobId()).isEqualTo(jobId);
        assertThat(record.getStatusRecordId()).isNull();
        assertThat(record.getStep()).isEqualTo("chapter-segmentation");
        assertThat(record.getRequest()).isNotNull();
        assertThat(record.getRequest().getInputBody()).isEqualTo("Chapter 1: The hero begins their journey in a smal");
        assertThat(record.getResponse()).isNotNull();
        assertThat(record.getResponse().getBody())
                .isEqualTo("This is a test response that is longer than 50 cha");
    }

    @Test
    void persistMultipleLlmCallRecords_shouldRetrieveByJobAndStep() {
        UUID jobId = createJob();

        loggingService.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "Segmentation system prompt",
                "Chapter text",
                "Segmentation response that is short",
                1000L,
                400,
                100
        );

        loggingService.logCall(
                jobId,
                "scene-analysis",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "scene-analysis.txt",
                "Analysis system prompt",
                "Segmentation XML result",
                "Analysis response that is also short",
                800L,
                200,
                80
        );

        assertThat(llmCallRepo.findByJobId(jobId)).hasSize(2);
        assertThat(llmCallRepo.findByJobIdAndStep(jobId, "chapter-segmentation")).singleElement()
                .extracting(LlmCallRecord::getStep)
                .isEqualTo("chapter-segmentation");
        assertThat(llmCallRepo.findByJobIdAndStep(jobId, "scene-analysis")).singleElement()
                .extracting(LlmCallRecord::getStep)
                .isEqualTo("scene-analysis");
    }

    @Test
    void persistLlmCallRecord_withoutJob_shouldSkipPersistence() {
        UUID nonExistentJobId = UUID.randomUUID();

        loggingService.logCall(
                nonExistentJobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "System prompt",
                "Input preview",
                "Response content",
                1500L,
                300,
                75
        );

        assertThat(llmCallRepo.findByJobId(nonExistentJobId)).isEmpty();
    }

    @Test
    void truncationBehavior_withExactSizeLimit_shouldNotTruncate() {
        UUID jobId = createJob();
        String exactSize50Response = "12345678901234567890123456789012345678901234567890";

        loggingService.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "System prompt",
                "Input preview",
                exactSize50Response,
                1000L,
                300,
                75
        );

        LlmCallRecord logged = llmCallRepo.findByJobId(jobId).getFirst();
        assertThat(logged.getResponse()).isNotNull();
        assertThat(logged.getResponse().getBody()).isEqualTo(exactSize50Response);
    }

    @Test
    @Commit
    void verifyLlmCallRecordRelationshipsAreEstablished() {
        UUID jobId = createJob();

        loggingService.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "Test system prompt",
                "Test input",
                "Test response",
                1250L,
                500,
                150
        );

        List<LlmCallRecord> retrievedRecords = llmCallRepo.findByJobId(jobId);
        assertThat(retrievedRecords).hasSize(1);
        LlmCallRecord persisted = retrievedRecords.getFirst();
        assertThat(persisted.getJobId()).isEqualTo(jobId);
        assertThat(llmCallRepo.hasOfJobRelation(persisted.getId(), jobId)).isTrue();
    }

    private UUID createJob() {
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(LocalDateTime.now());
        return jobRepo.save(job).getId();
    }
}
