package com.lorevault.api.infrastructure.persistence.neo4j;

import com.lorevault.api.configuration.properties.LoreVaultLlmLoggingProperties;
import com.lorevault.api.domain.ingestion.LlmCallRecord;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jMapper;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.*;
import com.lorevault.api.service.ingestion.LlmCallLoggingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for LlmCallRecord persistence with Neo4j.
 * Verifies end-to-end persistence, linking, and truncation behavior.
 */
@DataNeo4jTest
@Import({Neo4jContentPersistenceAdapter.class, Neo4jMapper.class})
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class LlmCallRecordPersistenceIntegrationTest {

    @SuppressWarnings("resource") // Testcontainers manages lifecycle
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
    private Neo4jContentPersistenceAdapter persistenceAdapter;

    @Autowired
    private LlmCallRecordGraphRepository llmCallRepo;

    @Autowired
    private IngestionJobGraphRepository jobRepo;

    @Autowired
    private org.neo4j.driver.Driver driver;

    @Autowired
    private StatusRecordGraphRepository statusRepo;

    // Mapper is provided via @Import but isn't needed directly here

    private LlmCallLoggingService loggingService;

    @BeforeEach
    void setUp() {
        // Clear database between tests
        llmCallRepo.deleteAll();
        statusRepo.deleteAll();
        jobRepo.deleteAll();

        // Use truncation config for this test
    var truncationProps = new LoreVaultLlmLoggingProperties(true, true, 50, true);
    loggingService = new LlmCallLoggingService(truncationProps, persistenceAdapter);
    }

    @Test
    void persistLlmCallRecord_shouldSaveAndRetrieve() {
        // Arrange
        UUID jobId = UUID.randomUUID();

        // Create job first
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(LocalDateTime.now());
        job = persistenceAdapter.createJob(job);

        // For now, skip status linkage and test without it
        // Act: Log an LLM call
        LlmCallRecord logged = loggingService.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant that detects scenes in narrative text...",
            "Chapter 1: The hero begins their journey in a small village...",
            "This is a test response that is longer than 50 characters and should be truncated in the integration test",
            1250L,
            500,
            150
        );

        // Assert: Verify the record was persisted
        assertThat(logged).isNotNull();
        assertThat(logged.getId()).isNotNull();

        // Retrieve via repository
        List<LlmCallRecord> retrieved = persistenceAdapter.findLlmCallsByJob(jobId);
        assertThat(retrieved).hasSize(1);
        
        LlmCallRecord record = retrieved.get(0);
        assertThat(record.getJobId()).isEqualTo(jobId);
        // Skip statusRecordId assertion for now - it should be null without a status record
        assertThat(record.getStatusRecordId()).isNull();
        assertThat(record.getStep()).isEqualTo("scene-detection-pass1");
        assertThat(record.getProvider()).isEqualTo("openai-compatible");
        assertThat(record.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(record.getTemperature()).isEqualTo(0.1);
        assertThat(record.getTopP()).isEqualTo(0.9);
        assertThat(record.getMaxTokens()).isEqualTo(6000);
        assertThat(record.getLatencyMs()).isEqualTo(1250L);
        assertThat(record.getInputTokens()).isEqualTo(500);
        assertThat(record.getOutputTokens()).isEqualTo(150);
        assertThat(record.getTokensEstimated()).isTrue();
        assertThat(record.getPromptTemplateId()).isEqualTo("scene-detection-pass1.txt");
        assertThat(record.getStoreRenderedPrompt()).isTrue();
        assertThat(record.getRenderedPrompt()).isEqualTo("You are an AI assistant that detects scenes in narrative text...");
        assertThat(record.getInputPreview()).isEqualTo("Chapter 1: The hero begins their journey in a small village...");
        
        // Verify truncation
        assertThat(record.getResponseBody()).hasSize(50);
        assertThat(record.getResponseBody()).isEqualTo("This is a test response that is longer than 50 cha");
        assertThat(record.getTruncated()).isTrue();
        assertThat(record.getResponseHash()).isNotNull().hasSize(64); // SHA-256 hex
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void persistMultipleLlmCallRecords_shouldRetrieveByJobAndStep() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(LocalDateTime.now());
        job = persistenceAdapter.createJob(job);

        // Act: Log two different pass calls for the same job
        LlmCallRecord pass1 = loggingService.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "Pass 1 system prompt",
            "Chapter text",
            "Pass 1 response that is short",
            1000L,
            400,
            100
        );

        LlmCallRecord pass2 = loggingService.logCall(
            jobId,
            "scene-detection-pass2",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass2.txt",
            "Pass 2 system prompt",
            "Pass 1 XML result",
            "Pass 2 response that is also short",
            800L,
            200,
            80
        );

        // Assert: Verify both records exist
        List<LlmCallRecord> allCalls = persistenceAdapter.findLlmCallsByJob(jobId);
        assertThat(allCalls).hasSize(2);

        // Verify by step filtering
        List<LlmCallRecord> pass1Calls = persistenceAdapter.findLlmCallsByJobAndStep(jobId, "scene-detection-pass1");
        assertThat(pass1Calls).hasSize(1);
        assertThat(pass1Calls.get(0).getStep()).isEqualTo("scene-detection-pass1");
        assertThat(pass1Calls.get(0).getResponseBody()).isEqualTo("Pass 1 response that is short");
        assertThat(pass1Calls.get(0).getTruncated()).isFalse(); // Short, not truncated

        List<LlmCallRecord> pass2Calls = persistenceAdapter.findLlmCallsByJobAndStep(jobId, "scene-detection-pass2");
        assertThat(pass2Calls).hasSize(1);
        assertThat(pass2Calls.get(0).getStep()).isEqualTo("scene-detection-pass2");
        assertThat(pass2Calls.get(0).getResponseBody()).isEqualTo("Pass 2 response that is also short");
        assertThat(pass2Calls.get(0).getTruncated()).isFalse(); // Short, not truncated
    }

    @Test 
    void persistLlmCallRecord_withoutJob_shouldStillSave() {
        // Arrange: Create a record for a non-existent job ID
        UUID nonExistentJobId = UUID.randomUUID();

        // Act: Try to log without creating the job first
        LlmCallRecord logged = loggingService.logCall(
            nonExistentJobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "System prompt",
            "Input preview",
            "Response content",
            1500L,
            300,
            75
        );

        // Assert: Should still be saved but without status link
        assertThat(logged).isNotNull();
        assertThat(logged.getJobId()).isEqualTo(nonExistentJobId);
        assertThat(logged.getStatusRecordId()).isNull();

        // Verify persistence
        List<LlmCallRecord> retrieved = persistenceAdapter.findLlmCallsByJob(nonExistentJobId);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getJobId()).isEqualTo(nonExistentJobId);
        assertThat(retrieved.get(0).getStatusRecordId()).isNull();
    }

    @Test
    void truncationBehavior_withExactSizeLimit_shouldNotTruncate() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(LocalDateTime.now());
        job = persistenceAdapter.createJob(job);

        String exactSize50Response = "12345678901234567890123456789012345678901234567890"; // exactly 50 chars

        // Act
        LlmCallRecord logged = loggingService.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "System prompt",
            "Input preview",
            exactSize50Response,
            1000L,
            300,
            75
        );

        // Assert: Should not be truncated at exact limit
        assertThat(logged.getResponseBody()).hasSize(50).isEqualTo(exactSize50Response);
        assertThat(logged.getTruncated()).isFalse();
        assertThat(logged.getResponseHash()).isNotNull();
    }

    @Test
    @Commit
    void verifyLlmCallRecordRelationshipsAreEstablished() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(LocalDateTime.now());
        job = persistenceAdapter.createJob(job);

        // Act: Log an LLM call
        LlmCallRecord record = loggingService.logCall(
            jobId,
            "scene-detection-pass-1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "Test system prompt",
            "Test input",
            "Test response",
            1250L,
            500,
            150
        );

    // Verify the record is persisted for the job
    List<LlmCallRecord> retrievedRecords = persistenceAdapter.findLlmCallsByJob(jobId);
    assertThat(retrievedRecords).hasSize(1);
    LlmCallRecord persisted = retrievedRecords.get(0);
    assertThat(persisted.getJobId()).isEqualTo(jobId);

    // Assert relationship existence via Cypher-based repository check
    boolean hasJobRel = llmCallRepo.hasOfJobRelation(persisted.getId(), jobId);
    assertThat(hasJobRel).isTrue();

    // And ensure the relationship also loads when using the aliasing query (defense in depth)
    var nodes = llmCallRepo.findByJobId(jobId);
    assertThat(nodes).hasSize(1);
    assertThat(nodes.get(0).getJob()).isNotNull();
    assertThat(nodes.get(0).getJob().getId()).isEqualTo(jobId);
    }
}