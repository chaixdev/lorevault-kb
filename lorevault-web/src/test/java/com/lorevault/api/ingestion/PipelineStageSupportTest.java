package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.job.IngestionJobService;

import com.lorevault.api.ingestion.scene.SceneLocalizationException;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.job.IngestionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
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

    @Test
    @DisplayName("Preserves structured failure payload for scene localization business exceptions")
    void runStage_sceneLocalizationException_persistsStructuredFailure() {
        PipelineStageSupport support = new PipelineStageSupport(ingestionJobService, eventPublisher);
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();

        support.runStage(this, "SCENE_DETECTION", jobId, chapterId, () -> {
            throw new SceneLocalizationException(
                    IngestionFailure.builder(
                                    "SCENE_LOCALIZATION_ANCHOR_NOT_FOUND",
                                    "Failed to localize scene 4 because start anchor 'anchor' was not found"
                            )
                            .exceptionType(SceneLocalizationException.class.getSimpleName())
                            .stage("SCENE_SEGMENTATION")
                            .detail("sceneIndex", 4)
                            .detail("startAnchor", "anchor")
                            .build()
            );
        }, exception -> true);

        verify(ingestionJobService).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.FAILED),
                contains("SCENE_DETECTION failed"),
                argThat(properties -> "SCENE_LOCALIZATION_ANCHOR_NOT_FOUND".equals(properties.get("failureCode"))
                        && "SCENE_SEGMENTATION".equals(properties.get("failureStage"))
                        && Integer.valueOf(4).equals(properties.get("failureDetail.sceneIndex"))
                        && "anchor".equals(properties.get("failureDetail.startAnchor")))
        );
    }
}
