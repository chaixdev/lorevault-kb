package com.lorevault.api.service.ingestion;

import com.lorevault.api.configuration.properties.LoreVaultLlmLoggingProperties;
import com.lorevault.api.domain.ingestion.LlmCallRecord;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.IngestionJobGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.LlmCallRecordGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.StatusRecordGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Service-level tests for LlmCallLoggingService covering business logic,
 * configuration-driven behavior, and truncation logic.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class LlmCallLoggingServiceTest {

    @Mock
    private IngestionJobGraphRepository jobRepo;

    @Mock
    private StatusRecordGraphRepository statusRepo;

    @Mock
    private LlmCallRecordGraphRepository llmCallRepo;

    private LlmCallLoggingService service;

    @BeforeEach
    void setUp() {
        // Default dev-friendly config: enabled, bodies persisted, no truncation, store prompts
        var props = new LoreVaultLlmLoggingProperties(true, true, -1, true);
        service = new LlmCallLoggingService(props, jobRepo, statusRepo, llmCallRepo);
    }

    @Test
    void logCall_withValidJobId_shouldPersistRecord() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID statusId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        StatusRecord status = new StatusRecord();
        status.setId(statusId);
        job.setCurrentStatus(status);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LlmCallRecord result = service.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant...",
            "Chapter text preview...",
            "<scenes><scene>Response content</scene></scenes>",
            1250L,
            500,
            150
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getJobId()).isEqualTo(jobId);
        assertThat(result.getStatusRecordId()).isEqualTo(statusId);
        assertThat(result.getStep()).isEqualTo("scene-detection-pass1");
        assertThat(result.getProvider()).isEqualTo("openai-compatible");
        assertThat(result.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(result.getTemperature()).isEqualTo(0.1);
        assertThat(result.getTopP()).isEqualTo(0.9);
        assertThat(result.getMaxTokens()).isEqualTo(6000);
        assertThat(result.getLatencyMs()).isEqualTo(1250L);
        assertThat(result.getInputTokens()).isEqualTo(500);
        assertThat(result.getOutputTokens()).isEqualTo(150);
        assertThat(result.getTokensEstimated()).isTrue();
        assertThat(result.getPromptTemplateId()).isEqualTo("scene-detection-pass1.txt");
        assertThat(result.getStoreRenderedPrompt()).isTrue();
        assertThat(result.getRenderedPrompt()).isEqualTo("You are an AI assistant...");
        assertThat(result.getInputPreview()).isEqualTo("Chapter text preview...");
        assertThat(result.getResponseBody()).isEqualTo("<scenes><scene>Response content</scene></scenes>");
        assertThat(result.getTruncated()).isFalse();
        assertThat(result.getResponseHash()).isNotNull().hasSize(64); // SHA-256 hex
        assertThat(result.getCreatedAt()).isNotNull();

        verify(llmCallRepo).save(any(LlmCallRecord.class));
    }

    @Test
    void logCall_withNullJobId_shouldSkipPersistence() {
        // Act
        LlmCallRecord result = service.logCall(
            null, // null jobId
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant...",
            "Chapter text preview...",
            "Response content",
            1250L,
            500,
            150
        );

        // Assert
        assertThat(result).isNull();
        verifyNoInteractions(jobRepo, statusRepo, llmCallRepo);
    }

    @Test
    void logCall_withDisabledLogging_shouldSkipPersistence() {
        // Arrange: disabled logging
        var disabledProps = new LoreVaultLlmLoggingProperties(false, true, -1, true);
        service = new LlmCallLoggingService(disabledProps, jobRepo, statusRepo, llmCallRepo);

        // Act
        LlmCallRecord result = service.logCall(
            UUID.randomUUID(),
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant...",
            "Chapter text preview...",
            "Response content",
            1250L,
            500,
            150
        );

        // Assert
        assertThat(result).isNull();
        verifyNoInteractions(jobRepo, statusRepo, llmCallRepo);
    }

    @Test
    void logCall_withBodiesDisabled_shouldOmitResponseBody() {
        // Arrange: bodies disabled
        var noBodiesProps = new LoreVaultLlmLoggingProperties(true, false, -1, true);
        service = new LlmCallLoggingService(noBodiesProps, jobRepo, statusRepo, llmCallRepo);

        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LlmCallRecord result = service.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant...",
            "Chapter text preview...",
            "Response content",
            1250L,
            500,
            150
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getResponseBody()).isNull();
        assertThat(result.getResponseHash()).isNull();
        assertThat(result.getTruncated()).isNull();
        assertThat(result.getInputPreview()).isEqualTo("Chapter text preview...");
        assertThat(result.getRenderedPrompt()).isEqualTo("You are an AI assistant...");
    }

    @Test
    void logCall_withRenderedPromptDisabled_shouldOmitRenderedPrompt() {
        // Arrange: rendered prompt disabled
        var noPromptProps = new LoreVaultLlmLoggingProperties(true, true, -1, false);
        service = new LlmCallLoggingService(noPromptProps, jobRepo, statusRepo, llmCallRepo);

        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LlmCallRecord result = service.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant...",
            "Chapter text preview...",
            "Response content",
            1250L,
            500,
            150
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStoreRenderedPrompt()).isFalse();
        assertThat(result.getRenderedPrompt()).isNull();
        assertThat(result.getResponseBody()).isEqualTo("Response content");
    }

    @Test
    void logCall_withTruncation_shouldTruncateAndSetFlag() {
        // Arrange: truncation at 20 chars
        var truncationProps = new LoreVaultLlmLoggingProperties(true, true, 20, true);
        service = new LlmCallLoggingService(truncationProps, jobRepo, statusRepo, llmCallRepo);

        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String longResponse = "This is a very long response that exceeds 20 characters and should be truncated";

        // Act
        LlmCallRecord result = service.logCall(
            jobId,
            "scene-detection-pass2",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass2.txt",
            "You are an AI assistant...",
            "Pass 1 XML result...",
            longResponse,
            1500L,
            400,
            200
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getResponseBody()).hasSize(20).isEqualTo("This is a very long ");
        assertThat(result.getTruncated()).isTrue();
        assertThat(result.getResponseHash()).isNotNull().hasSize(64);
        
        // Hash should be of the original full response, not the truncated one
        String expectedHash = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"; // Will be different, but should be consistent
        assertThat(result.getResponseHash()).isNotEqualTo(expectedHash).hasSize(64);
    }

    @Test
    void logCall_withInputPreviewTruncation_shouldTruncateInputPreview() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Create input longer than 1000 chars (internal preview limit)
        String longInput = "x".repeat(1500);

        // Act
        LlmCallRecord result = service.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant...",
            longInput,
            "Short response",
            1000L,
            375,
            50
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getInputPreview()).hasSize(1000);
        assertThat(result.getInputPreview()).isEqualTo("x".repeat(1000));
        assertThat(result.getResponseBody()).isEqualTo("Short response");
        assertThat(result.getTruncated()).isFalse(); // Response was not truncated
    }

    @Test
    void logCall_withJobNotFound_shouldStillPersistWithoutStatusLink() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LlmCallRecord result = service.logCall(
            jobId,
            "scene-detection-pass1",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass1.txt",
            "You are an AI assistant...",
            "Chapter text preview...",
            "Response content",
            1250L,
            500,
            150
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getJobId()).isEqualTo(jobId);
        assertThat(result.getStatusRecordId()).isNull(); // No status link when job not found
        verify(llmCallRepo).save(any(LlmCallRecord.class));
    }

    @Test
    void logCall_withJobFoundButNoCurrentStatus_shouldPersistWithoutStatusLink() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setCurrentStatus(null); // No current status

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(statusRepo.findStatusHistoryForJob(jobId)).thenReturn(List.of());
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LlmCallRecord result = service.logCall(
            jobId,
            "scene-detection-pass2",
            "openai-compatible",
            "gpt-4o-mini",
            0.1,
            0.9,
            6000,
            "scene-detection-pass2.txt",
            "You are an AI assistant...",
            "Pass 1 XML result...",
            "Response content",
            1500L,
            400,
            200
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getJobId()).isEqualTo(jobId);
        assertThat(result.getStatusRecordId()).isNull();
        verify(statusRepo).findStatusHistoryForJob(jobId);
        verify(llmCallRepo).save(any(LlmCallRecord.class));
    }
}