package com.lorevault.api.ingestion;

import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineStageSupport")
class PipelineStageSupportTest {

    @Mock
    private IngestionJobService ingestionJobService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("Publishes failure event and failed status when stage work throws")
    void runStage_onException_publishesFailureEventAndFailedStatus() {
        PipelineStageSupport support = new PipelineStageSupport(ingestionJobService, eventPublisher);
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();

        support.runStage(this, "EMBEDDING", jobId, chapterId, () -> {
            throw new RuntimeException("boom");
        }, exception -> false);

        ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        IngestionFailedEvent event = eventCaptor.getValue();
        assertThat(event.getJobId()).isEqualTo(jobId);
        assertThat(event.getChapterId()).isEqualTo(chapterId);
        assertThat(event.getFailedStage()).isEqualTo("EMBEDDING");
        assertThat(event.getErrorMessage()).isEqualTo("boom");
        assertThat(event.isRetryable()).isEqualTo(false);

        verify(ingestionJobService).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.FAILED),
                contains("EMBEDDING failed: boom"),
                anyMap()
        );
    }
}
