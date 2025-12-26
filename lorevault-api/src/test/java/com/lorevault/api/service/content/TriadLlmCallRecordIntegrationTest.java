package com.lorevault.api.service.content;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.ingestion.LlmCallRecord;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.service.ingestion.IngestionJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that LlmCallRecord entities are properly linked 
 * to SCENE_TRIAD_ANALYSIS StatusRecord entities with correct triad metadata.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@Testcontainers
@DisplayName("Triad LLM Call Record Integration")
class TriadLlmCallRecordIntegrationTest {

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
    private ContentPersistencePort contentPersistencePort;

    @Autowired
    private IngestionJobService ingestionJobService;

    private UUID testJobId;
    private UUID testChapterId;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testChapterId = UUID.randomUUID();

        // Create test job and chapter
        IngestionJob job = new IngestionJob();
        job.setId(testJobId);
        job.setChapterId(testChapterId);
        job.setCreatedAt(LocalDateTime.now());

        contentPersistencePort.createJobWithChapter(job, testChapterId);
    }

    @Test
    @Transactional
    @DisplayName("Should link LlmCallRecord to SCENE_TRIAD_ANALYSIS StatusRecord with triad metadata")
    void shouldLinkLlmCallRecordToTriadStatusRecord() {
        // Arrange - Create a SCENE_TRIAD_ANALYSIS status record with triad metadata
        UUID scene1Id = UUID.randomUUID();
        UUID scene2Id = UUID.randomUUID();
        UUID scene3Id = UUID.randomUUID();

        Map<String, Object> triadProps = Map.of(
            "triadIndex", 0,
            "prevSceneId", scene1Id,
            "currentSceneId", scene2Id,
            "nextSceneId", scene3Id
        );

        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
            triadProps
        );

        // Get the current status record that was just created
        IngestionJob job = contentPersistencePort.findJob(testJobId).orElseThrow();
        StatusRecord currentStatus = job.getCurrentStatus();
        assertThat(currentStatus).isNotNull();
        assertThat(currentStatus.getStatus()).isEqualTo(IngestionStatus.SCENE_TRIAD_ANALYSIS);

        // Act - Create an LLM call record (simulating what LlmCallLoggingService does)
        LlmCallRecord llmCallRecord = new LlmCallRecord();
        llmCallRecord.setId(UUID.randomUUID());
        llmCallRecord.setJobId(testJobId);
        llmCallRecord.setStatusRecordId(currentStatus.getId()); // Link to the triad status
        llmCallRecord.setStep("scene-detection-pass2");
        llmCallRecord.setProvider("openai-compatible");
        llmCallRecord.setModel("llama-3.3-70b-versatile");
        llmCallRecord.setTemperature(0.1);
        llmCallRecord.setTopP(0.9);
        llmCallRecord.setMaxTokens(6000);
        llmCallRecord.setPromptTemplateId("scene-detection-pass2.txt");
        llmCallRecord.setRenderedPrompt("System prompt for triad analysis");
        llmCallRecord.setInputPreview("[userTemplate=scene-detection-pass2-user] Triad user input...");
        llmCallRecord.setResponseBody("<scene_analysis>...</scene_analysis>");
        llmCallRecord.setLatencyMs(1500L);
        llmCallRecord.setCreatedAt(LocalDateTime.now());

        contentPersistencePort.addLlmCallRecord(llmCallRecord);

        // Assert - Verify the linking worked correctly
        List<LlmCallRecord> callRecords = contentPersistencePort.findLlmCallsByJobAndStep(testJobId, "scene-detection-pass2");
        assertThat(callRecords).hasSize(1);

        LlmCallRecord savedRecord = callRecords.get(0);
        assertThat(savedRecord.getStatusRecordId()).isEqualTo(currentStatus.getId());
        assertThat(savedRecord.getStep()).isEqualTo("scene-detection-pass2");
        assertThat(savedRecord.getInputPreview()).startsWith("[userTemplate=scene-detection-pass2-user]");

        // Verify the status record contains the expected triad metadata
        List<StatusRecord> statusHistory = contentPersistencePort.findStatusHistoryForJob(testJobId);
        StatusRecord triadStatusRecord = statusHistory.stream()
            .filter(status -> status.getStatus() == IngestionStatus.SCENE_TRIAD_ANALYSIS)
            .findFirst()
            .orElseThrow();

        assertThat(triadStatusRecord.getProperties()).containsEntry("triadIndex", 0);
        assertThat(triadStatusRecord.getProperties()).containsEntry("prevSceneId", scene1Id);
        assertThat(triadStatusRecord.getProperties()).containsEntry("currentSceneId", scene2Id);
        assertThat(triadStatusRecord.getProperties()).containsEntry("nextSceneId", scene3Id);
        assertThat(triadStatusRecord.getStepDescription()).isEqualTo("Triad analysis for scenes [prev, curr, next]");
    }

    @Test
    @Transactional
    @DisplayName("Should handle multiple triad LLM calls with different status records")
    void shouldHandleMultipleTriadLlmCallsWithDifferentStatusRecords() {
        // Arrange - Create two SCENE_TRIAD_ANALYSIS status records
        UUID scene1Id = UUID.randomUUID();
        UUID scene2Id = UUID.randomUUID();
        UUID scene3Id = UUID.randomUUID();

        // First triad
        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
            Map.of("triadIndex", 0, "prevSceneId", scene1Id, "currentSceneId", scene2Id, "nextSceneId", scene3Id)
        );

        IngestionJob job1 = contentPersistencePort.findJob(testJobId).orElseThrow();
        StatusRecord firstTriadStatus = job1.getCurrentStatus();

        // Second triad
        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
            Map.of("triadIndex", 1, "prevSceneId", scene2Id, "currentSceneId", scene3Id, "nextSceneId", (UUID) null)
        );

        IngestionJob job2 = contentPersistencePort.findJob(testJobId).orElseThrow();
        StatusRecord secondTriadStatus = job2.getCurrentStatus();

        // Act - Create LLM call records for both triads
        LlmCallRecord firstCall = createTriadLlmCallRecord(firstTriadStatus.getId());
        LlmCallRecord secondCall = createTriadLlmCallRecord(secondTriadStatus.getId());

        contentPersistencePort.addLlmCallRecord(firstCall);
        contentPersistencePort.addLlmCallRecord(secondCall);

        // Assert - Verify both records are linked correctly
        List<LlmCallRecord> allTriadCalls = contentPersistencePort.findLlmCallsByJobAndStep(testJobId, "scene-detection-pass2");
        assertThat(allTriadCalls).hasSize(2);

        // Verify each call is linked to the correct status record
        assertThat(allTriadCalls).extracting(LlmCallRecord::getStatusRecordId)
            .containsExactlyInAnyOrder(firstTriadStatus.getId(), secondTriadStatus.getId());

        // Verify the status records have different triad indices
        List<StatusRecord> triadStatuses = contentPersistencePort.findStatusHistoryForJob(testJobId).stream()
            .filter(status -> status.getStatus() == IngestionStatus.SCENE_TRIAD_ANALYSIS)
            .toList();

        assertThat(triadStatuses).hasSize(2);
        assertThat(triadStatuses).extracting(status -> status.getProperties().get("triadIndex"))
            .containsExactlyInAnyOrder(0, 1);
    }

    @Test
    @Transactional
    @DisplayName("Should persist triad metadata in status properties for query and debugging")
    void shouldPersistTriadMetadataInStatusProperties() {
        // Arrange
        UUID prevSceneId = UUID.randomUUID();
        UUID currSceneId = UUID.randomUUID();
        UUID nextSceneId = UUID.randomUUID();

        Map<String, Object> triadMetadata = Map.of(
            "triadIndex", 2,
            "prevSceneId", prevSceneId,
            "currentSceneId", currSceneId,
            "nextSceneId", nextSceneId
        );

        // Act
        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
            triadMetadata
        );

        // Assert - Verify metadata is retrievable and correct
        List<StatusRecord> statusHistory = contentPersistencePort.findStatusHistoryForJob(testJobId);
        StatusRecord triadStatus = statusHistory.stream()
            .filter(status -> status.getStatus() == IngestionStatus.SCENE_TRIAD_ANALYSIS)
            .findFirst()
            .orElseThrow();

        assertThat(triadStatus.getProperties())
            .containsEntry("triadIndex", 2)
            .containsEntry("prevSceneId", prevSceneId)
            .containsEntry("currentSceneId", currSceneId)
            .containsEntry("nextSceneId", nextSceneId);

        // Verify the specific scene IDs can be extracted for debugging/analysis
        Object retrievedPrevId = triadStatus.getProperties().get("prevSceneId");
        Object retrievedCurrId = triadStatus.getProperties().get("currentSceneId");
        Object retrievedNextId = triadStatus.getProperties().get("nextSceneId");

        assertThat(retrievedPrevId).isEqualTo(prevSceneId);
        assertThat(retrievedCurrId).isEqualTo(currSceneId);
        assertThat(retrievedNextId).isEqualTo(nextSceneId);
    }

    private LlmCallRecord createTriadLlmCallRecord(UUID statusRecordId) {
        LlmCallRecord record = new LlmCallRecord();
        record.setId(UUID.randomUUID());
        record.setJobId(testJobId);
        record.setStatusRecordId(statusRecordId);
        record.setStep("scene-detection-pass2");
        record.setProvider("openai-compatible");
        record.setModel("llama-3.3-70b-versatile");
        record.setTemperature(0.1);
        record.setTopP(0.9);
        record.setMaxTokens(6000);
        record.setPromptTemplateId("scene-detection-pass2.txt");
        record.setRenderedPrompt("System prompt content");
        record.setInputPreview("[userTemplate=scene-detection-pass2-user] User content preview");
        record.setResponseBody("<scene_analysis>mock response</scene_analysis>");
        record.setLatencyMs(1200L);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }
}