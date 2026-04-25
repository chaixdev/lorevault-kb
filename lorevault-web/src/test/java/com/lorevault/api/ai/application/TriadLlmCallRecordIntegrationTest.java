package com.lorevault.api.ai.application;
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

import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.infrastructure.LlmCallRecordGraphRepository;
import com.lorevault.api.ingestion.infrastructure.StatusRecordGraphRepository;
import com.lorevault.api.ingestion.application.IngestionJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;
import com.lorevault.api.integration.TestConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@Import(TestConfig.class)
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
    private IngestionJobGraphRepository jobRepo;

    @Autowired
    private StatusRecordGraphRepository statusRepo;

    @Autowired
    private LlmCallRecordGraphRepository llmCallRepo;

    @Autowired
    private IngestionJobService ingestionJobService;

    private UUID testJobId;
    private UUID testChapterId;
    private UUID scene1Id;
    private UUID scene2Id;
    private UUID scene3Id;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testChapterId = UUID.randomUUID();
        scene1Id = UUID.randomUUID();
        scene2Id = UUID.randomUUID();
        scene3Id = UUID.randomUUID();

        // Create test job and chapter
        IngestionJob job = new IngestionJob();
        job.setId(testJobId);
        job.setChapterId(testChapterId);
        job.setCreatedAt(LocalDateTime.now());

        jobRepo.save(job);
    }

    @Test
    @DisplayName("Should link LlmCallRecord to SCENE_TRIAD_ANALYSIS StatusRecord with triad metadata")
    void shouldLinkLlmCallRecordToTriadStatusRecord() {
        // Arrange - Create a SCENE_TRIAD_ANALYSIS status record with triad metadata
        Map<String, Object> triadProps = Map.of(
            "triadIndex", 0,
                "prevSceneId", scene1Id,
                "currentSceneId", scene2Id,
                "nextSceneId", scene3Id,
                "prevSceneIndex", 0,
                "currentSceneIndex", 1,
                "nextSceneIndex", 2
        );

        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
            triadProps
        );

        // Get the current status record that was just created
        IngestionJob job = jobRepo.findById(testJobId).orElseThrow();
        StatusRecord currentStatus = job.getCurrentStatus();
        assertThat(currentStatus).isNotNull();
        assertThat(currentStatus.getStatus()).isEqualTo(IngestionStatus.SCENE_TRIAD_ANALYSIS);

        // Act - Create an LLM call record (simulating what LlmCallLoggingService does)
        LlmCallRecord llmCallRecord = new LlmCallRecord();
        llmCallRecord.setId(UUID.randomUUID());
        llmCallRecord.setJobId(testJobId);
        llmCallRecord.setStatusRecordId(currentStatus.getId()); // Link to the triad status
        llmCallRecord.setStep("scene-analysis");
        llmCallRecord.setProvider("openai-compatible");
        llmCallRecord.setModel("llama-3.3-70b-versatile");
        llmCallRecord.setTemperature(0.1);
        llmCallRecord.setTopP(0.9);
        llmCallRecord.setMaxTokens(6000);
        llmCallRecord.setPromptTemplateId("scene-analysis.txt");
        llmCallRecord.setRenderedPrompt("System prompt for triad analysis");
        llmCallRecord.setInputPreview("[userTemplate=scene-analysis-user] Triad user input...");
        llmCallRecord.setResponseBody("<scene_analysis>...</scene_analysis>");
        llmCallRecord.setLatencyMs(1500L);
        llmCallRecord.setCreatedAt(LocalDateTime.now());

        llmCallRecord.setJob(job);
        llmCallRecord.setStatus(currentStatus);
        llmCallRepo.save(llmCallRecord);

        // Assert - Verify the linking worked correctly
        List<LlmCallRecord> callRecords = llmCallRepo.findByJobIdAndStep(testJobId, "scene-analysis");
        assertThat(callRecords).hasSize(1);

        LlmCallRecord savedRecord = callRecords.get(0);
        assertThat(savedRecord.getStatusRecordId()).isEqualTo(currentStatus.getId());
        assertThat(savedRecord.getStep()).isEqualTo("scene-analysis");
        assertThat(savedRecord.getInputPreview()).startsWith("[userTemplate=scene-analysis-user]");

        // Verify graph relationships exist
        assertThat(llmCallRepo.hasOfJobRelation(savedRecord.getId(), testJobId)).isTrue();
        assertThat(llmCallRepo.hasOfStatusRelation(savedRecord.getId(), currentStatus.getId())).isTrue();

        // Verify the status record contains the expected triad metadata
        List<StatusRecord> statusHistory = statusRepo.findStatusHistoryForJob(testJobId);
        StatusRecord triadStatusRecord = statusHistory.stream()
            .filter(status -> status.getStatus() == IngestionStatus.SCENE_TRIAD_ANALYSIS)
            .findFirst()
            .orElseThrow();

        assertThat(triadStatusRecord.getProperties()).containsEntry("triadIndex", "0");
        assertThat(triadStatusRecord.getProperties()).containsEntry("prevSceneId", scene1Id.toString());
        assertThat(triadStatusRecord.getProperties()).containsEntry("currentSceneId", scene2Id.toString());
        assertThat(triadStatusRecord.getProperties()).containsEntry("nextSceneId", scene3Id.toString());
        assertThat(triadStatusRecord.getProperties()).containsEntry("prevSceneIndex", "0");
        assertThat(triadStatusRecord.getProperties()).containsEntry("currentSceneIndex", "1");
        assertThat(triadStatusRecord.getProperties()).containsEntry("nextSceneIndex", "2");
        assertThat(triadStatusRecord.getStepDescription()).isEqualTo("Triad analysis for scenes [prev, curr, next]");

        Optional<StatusRecord> lookupByCurrentScene = statusRepo
                .findLatestTriadStatusByCurrentSceneId(testJobId, scene2Id.toString());
        assertThat(lookupByCurrentScene).isPresent();
        assertThat(lookupByCurrentScene.orElseThrow().getId()).isEqualTo(currentStatus.getId());
    }

    @Test
    @DisplayName("Should handle multiple triad LLM calls with different status records")
    void shouldHandleMultipleTriadLlmCallsWithDifferentStatusRecords() {
        // Arrange - Create two SCENE_TRIAD_ANALYSIS status records
        // First triad
        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
        Map.of(
                "triadIndex", 0,
                "prevSceneId", scene1Id,
                "currentSceneId", scene2Id,
                "nextSceneId", scene3Id,
                "prevSceneIndex", 0,
                "currentSceneIndex", 1,
                "nextSceneIndex", 2
        )
        );

        IngestionJob job1 = jobRepo.findById(testJobId).orElseThrow();
        StatusRecord firstTriadStatus = job1.getCurrentStatus();

        // Second triad
        Map<String, Object> secondTriadProps = new java.util.HashMap<>();
        secondTriadProps.put("triadIndex", 1);
        secondTriadProps.put("prevSceneId", scene2Id);
        secondTriadProps.put("currentSceneId", scene3Id);
        secondTriadProps.put("nextSceneId", null);
        secondTriadProps.put("prevSceneIndex", 1);
        secondTriadProps.put("currentSceneIndex", 2);
        secondTriadProps.put("nextSceneIndex", null);

        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
            secondTriadProps
        );

        IngestionJob job2 = jobRepo.findById(testJobId).orElseThrow();
        StatusRecord secondTriadStatus = job2.getCurrentStatus();

        // Act - Create LLM call records for both triads
        LlmCallRecord firstCall = createTriadLlmCallRecord(firstTriadStatus.getId());
        LlmCallRecord secondCall = createTriadLlmCallRecord(secondTriadStatus.getId());

        firstCall.setJob(job1);
        firstCall.setStatus(firstTriadStatus);
        secondCall.setJob(job2);
        secondCall.setStatus(secondTriadStatus);
        llmCallRepo.save(firstCall);
        llmCallRepo.save(secondCall);

        // Assert - Verify both records are linked correctly
        List<LlmCallRecord> allTriadCalls = llmCallRepo.findByJobIdAndStep(testJobId, "scene-analysis");
        assertThat(allTriadCalls).hasSize(2);

        // Verify each call is linked to the correct status record
        assertThat(allTriadCalls).extracting(LlmCallRecord::getStatusRecordId)
            .containsExactlyInAnyOrder(firstTriadStatus.getId(), secondTriadStatus.getId());

        // Verify the status records have different triad indices
        List<StatusRecord> triadStatuses = statusRepo.findStatusHistoryForJob(testJobId).stream()
            .filter(status -> status.getStatus() == IngestionStatus.SCENE_TRIAD_ANALYSIS)
            .toList();

        assertThat(triadStatuses).hasSize(2);
        assertThat(triadStatuses).extracting(status -> status.getProperties().get("triadIndex"))
            .containsExactlyInAnyOrder("0", "1");
    }

    @Test
    @DisplayName("Should persist triad metadata in status properties for query and debugging")
    void shouldPersistTriadMetadataInStatusProperties() {
        // Arrange
        int prevSceneIndex = 9;
        int currSceneIndex = 10;
        int nextSceneIndex = 11;

        Map<String, Object> triadMetadata = Map.of(
            "triadIndex", 2,
                "prevSceneId", scene1Id,
                "currentSceneId", scene2Id,
                "nextSceneId", scene3Id,
                "prevSceneIndex", prevSceneIndex,
                "currentSceneIndex", currSceneIndex,
                "nextSceneIndex", nextSceneIndex
        );

        // Act
        ingestionJobService.updateJobStatus(
            testJobId,
            IngestionStatus.SCENE_TRIAD_ANALYSIS,
            "Triad analysis for scenes [prev, curr, next]",
            triadMetadata
        );

        // Assert - Verify metadata is retrievable and correct
        List<StatusRecord> statusHistory = statusRepo.findStatusHistoryForJob(testJobId);
        StatusRecord triadStatus = statusHistory.stream()
            .filter(status -> status.getStatus() == IngestionStatus.SCENE_TRIAD_ANALYSIS)
            .findFirst()
            .orElseThrow();

        assertThat(triadStatus.getProperties())
            .containsEntry("triadIndex", "2")
                .containsEntry("prevSceneId", scene1Id.toString())
                .containsEntry("currentSceneId", scene2Id.toString())
                .containsEntry("nextSceneId", scene3Id.toString())
                .containsEntry("prevSceneIndex", String.valueOf(prevSceneIndex))
                .containsEntry("currentSceneIndex", String.valueOf(currSceneIndex))
                .containsEntry("nextSceneIndex", String.valueOf(nextSceneIndex));

        // Verify the specific scene IDs can be extracted for debugging/analysis
        String retrievedPrev = triadStatus.getProperties().get("prevSceneIndex");
        String retrievedCurr = triadStatus.getProperties().get("currentSceneIndex");
        String retrievedNext = triadStatus.getProperties().get("nextSceneIndex");

        assertThat(retrievedPrev).isEqualTo(String.valueOf(prevSceneIndex));
        assertThat(retrievedCurr).isEqualTo(String.valueOf(currSceneIndex));
        assertThat(retrievedNext).isEqualTo(String.valueOf(nextSceneIndex));
    }

    private LlmCallRecord createTriadLlmCallRecord(UUID statusRecordId) {
        LlmCallRecord record = new LlmCallRecord();
        record.setId(UUID.randomUUID());
        record.setJobId(testJobId);
        record.setStatusRecordId(statusRecordId);
        record.setStep("scene-analysis");
        record.setProvider("openai-compatible");
        record.setModel("llama-3.3-70b-versatile");
        record.setTemperature(0.1);
        record.setTopP(0.9);
        record.setMaxTokens(6000);
        record.setPromptTemplateId("scene-analysis.txt");
        record.setRenderedPrompt("System prompt content");
        record.setInputPreview("[userTemplate=scene-analysis-user] User content preview");
        record.setResponseBody("<scene_analysis>mock response</scene_analysis>");
        record.setLatencyMs(1200L);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }
}
