package com.lorevault.api.web.query.job;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobStatusBroadcaster Tests")
class JobStatusBroadcasterTest {

    private JobStatusBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new JobStatusBroadcaster();
    }

    @Test
    @DisplayName("register returns emitter and tracks connection count")
    void registerTracksConnections() {
        SseEmitter first = broadcaster.register();
        SseEmitter second = broadcaster.register();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(broadcaster.getConnectionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("stage completed broadcasts without dropping healthy emitters")
    void onStageCompletedBroadcasts() {
        broadcaster.register();

        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        StageCompletedEvent event = new StageCompletedEvent(
                this, jobId, chapterId, bookId, StageKey.SCENE_SEGMENTATION,
                StepResult.success(StageKey.SCENE_SEGMENTATION, "Detected 5 scenes", Map.of("scenesDetected", 5), 1234L)
        );

        broadcaster.onStageCompleted(event);

        assertThat(broadcaster.getConnectionCount()).isEqualTo(1);
    }
}
