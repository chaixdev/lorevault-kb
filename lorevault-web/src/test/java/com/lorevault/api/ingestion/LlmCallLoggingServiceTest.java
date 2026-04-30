package com.lorevault.api.ingestion;

import com.lorevault.api.config.LoreVaultLlmLoggingProperties;
import com.lorevault.api.ingestion.job.IngestionJob;
import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;
import com.lorevault.api.ingestion.job.StatusRecord;
import com.lorevault.api.ingestion.job.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.infrastructure.LlmCallLoggingService;
import com.lorevault.api.ingestion.infrastructure.LlmCallRecordGraphRepository;
import com.lorevault.api.ingestion.job.StatusRecordGraphRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        var props = new LoreVaultLlmLoggingProperties(true, true, -1, true);
        service = new LlmCallLoggingService(props, jobRepo, statusRepo, llmCallRepo);
    }

    @Test
    void logCall_withValidJobId_shouldPersistRecord() {
        UUID jobId = UUID.randomUUID();
        UUID statusId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        StatusRecord status = new StatusRecord();
        status.setId(statusId);
        job.setCurrentStatus(status);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "You are an AI assistant...",
                "Chapter text preview...",
                "<scenes><scene>Response content</scene></scenes>",
                1250L,
                500,
                150
        );

        ArgumentCaptor<LlmCallRecord> captor = ArgumentCaptor.forClass(LlmCallRecord.class);
        verify(llmCallRepo).save(captor.capture());
        LlmCallRecord result = captor.getValue();

        assertThat(result.getJobId()).isEqualTo(jobId);
        assertThat(result.getStatusRecordId()).isEqualTo(statusId);
        assertThat(result.getStep()).isEqualTo("chapter-segmentation");
        assertThat(result.getProvider()).isEqualTo("openai-compatible");
        assertThat(result.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(result.getRequest()).isNotNull();
        assertThat(result.getRequest().getInputBody()).isEqualTo("Chapter text preview...");
        assertThat(result.getRequest().getInputHash()).isNotBlank();
        assertThat(result.getRequest().getInputTruncated()).isFalse();
        assertThat(result.getResponse()).isNotNull();
        assertThat(result.getResponse().getBody()).isEqualTo("<scenes><scene>Response content</scene></scenes>");
        assertThat(result.getResponse().getBodyHash()).isNotBlank();
        assertThat(result.getResponse().getTruncated()).isFalse();
    }

    @Test
    void logCall_withNullJobId_shouldSkipPersistence() {
        service.logCall(
                null,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "prompt",
                "input",
                "response",
                1250L,
                500,
                150
        );

        verifyNoInteractions(jobRepo, statusRepo, llmCallRepo);
    }

    @Test
    void logCall_withDisabledLogging_shouldSkipPersistence() {
        service = new LlmCallLoggingService(new LoreVaultLlmLoggingProperties(false, true, -1, true), jobRepo, statusRepo, llmCallRepo);

        service.logCall(
                UUID.randomUUID(),
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "prompt",
                "input",
                "response",
                1250L,
                500,
                150
        );

        verifyNoInteractions(jobRepo, statusRepo, llmCallRepo);
    }

    @Test
    void logCall_withBodiesDisabled_shouldOmitResponseBody() {
        service = new LlmCallLoggingService(new LoreVaultLlmLoggingProperties(true, false, -1, true), jobRepo, statusRepo, llmCallRepo);
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(jobWithCurrentStatus(jobId)));
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "prompt",
                "input",
                "response",
                1250L,
                500,
                150
        );

        ArgumentCaptor<LlmCallRecord> captor = ArgumentCaptor.forClass(LlmCallRecord.class);
        verify(llmCallRepo).save(captor.capture());
        LlmCallRecord result = captor.getValue();
        assertThat(result.getResponse()).isNotNull();
        assertThat(result.getResponse().getBody()).isNull();
        assertThat(result.getResponse().getBodyHash()).isNull();
        assertThat(result.getResponse().getTruncated()).isNull();
    }

    @Test
    void logCall_withRenderedPromptDisabled_shouldOmitRenderedPrompt() {
        service = new LlmCallLoggingService(new LoreVaultLlmLoggingProperties(true, true, -1, false), jobRepo, statusRepo, llmCallRepo);
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(jobWithCurrentStatus(jobId)));
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "prompt",
                "input",
                "response",
                1250L,
                500,
                150
        );

        ArgumentCaptor<LlmCallRecord> captor = ArgumentCaptor.forClass(LlmCallRecord.class);
        verify(llmCallRepo).save(captor.capture());
        assertThat(captor.getValue().getStoreRenderedPrompt()).isFalse();
        assertThat(captor.getValue().getRequest()).isNotNull();
        assertThat(captor.getValue().getRequest().getRenderedPrompt()).isNull();
    }

    @Test
    void logCall_withLongResponse_shouldTruncateAndPersistHash() {
        service = new LlmCallLoggingService(new LoreVaultLlmLoggingProperties(true, true, 20, true), jobRepo, statusRepo, llmCallRepo);
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(jobWithCurrentStatus(jobId)));
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "prompt",
                "input",
                "This is a very long response that exceeds 20 characters and should be truncated",
                1500L,
                400,
                200
        );

        ArgumentCaptor<LlmCallRecord> captor = ArgumentCaptor.forClass(LlmCallRecord.class);
        verify(llmCallRepo).save(captor.capture());
        LlmCallRecord result = captor.getValue();
        assertThat(result.getResponse()).isNotNull();
        assertThat(result.getResponse().getBody()).isEqualTo("This is a very long ");
        assertThat(result.getResponse().getBodyHash()).isNotBlank();
        assertThat(result.getResponse().getTruncated()).isTrue();
    }

    @Test
    void logCall_withInputPreview_shouldHonorConfiguredLimit() {
        service = new LlmCallLoggingService(new LoreVaultLlmLoggingProperties(true, true, 1000, true), jobRepo, statusRepo, llmCallRepo);
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(jobWithCurrentStatus(jobId)));
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "prompt",
                "x".repeat(1500),
                "Short response",
                1000L,
                375,
                50
        );

        ArgumentCaptor<LlmCallRecord> captor = ArgumentCaptor.forClass(LlmCallRecord.class);
        verify(llmCallRepo).save(captor.capture());
        assertThat(captor.getValue().getRequest()).isNotNull();
        assertThat(captor.getValue().getRequest().getInputBody()).hasSize(1000);
        assertThat(captor.getValue().getRequest().getInputHash()).isNotBlank();
        assertThat(captor.getValue().getRequest().getInputTruncated()).isTrue();
        assertThat(captor.getValue().getResponse()).isNotNull();
        assertThat(captor.getValue().getResponse().getBody()).isEqualTo("Short response");
    }

    @Test
    void logCall_withJobFoundButNoCurrentStatus_shouldPersistWithoutStatusLink() {
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setCurrentStatus(null);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(statusRepo.findStatusHistoryForJob(jobId)).thenReturn(List.of());
        when(llmCallRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.logCall(
                jobId,
                "scene-analysis",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "scene-analysis.txt",
                "prompt",
                "input",
                "Response content",
                1500L,
                400,
                200
        );

        ArgumentCaptor<LlmCallRecord> captor = ArgumentCaptor.forClass(LlmCallRecord.class);
        verify(llmCallRepo).save(captor.capture());
        assertThat(captor.getValue().getJobId()).isEqualTo(jobId);
        assertThat(captor.getValue().getStatusRecordId()).isNull();
        verify(statusRepo).findStatusHistoryForJob(jobId);
    }

    @Test
    void logCall_withJobNotFound_shouldSkipPersistenceWithoutStatusLookup() {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());

        service.logCall(
                jobId,
                "chapter-segmentation",
                "openai-compatible",
                "gpt-4o-mini",
                0.1,
                0.9,
                6000,
                "chapter-segmentation.txt",
                "prompt",
                "input",
                "Response content",
                1250L,
                500,
                150
        );

        verify(llmCallRepo, never()).save(any());
        verify(statusRepo, never()).findStatusHistoryForJob(jobId);
    }

    private IngestionJob jobWithCurrentStatus(UUID jobId) {
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        StatusRecord currentStatus = new StatusRecord();
        currentStatus.setId(UUID.randomUUID());
        job.setCurrentStatus(currentStatus);
        return job;
    }
}
