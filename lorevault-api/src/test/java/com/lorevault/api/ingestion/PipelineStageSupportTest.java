package com.lorevault.api.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanWrapperImpl;
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
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        assertThat(eventBean.getPropertyValue("jobId")).isEqualTo(jobId);
        assertThat(eventBean.getPropertyValue("chapterId")).isEqualTo(chapterId);
        assertThat(eventBean.getPropertyValue("failedStage")).isEqualTo("EMBEDDING");
        assertThat(eventBean.getPropertyValue("errorMessage")).isEqualTo("boom");
        assertThat(eventBean.getPropertyValue("retryable")).isEqualTo(false);

        verify(ingestionJobService).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.FAILED),
                contains("EMBEDDING failed: boom"),
                anyMap()
        );
    }
}
