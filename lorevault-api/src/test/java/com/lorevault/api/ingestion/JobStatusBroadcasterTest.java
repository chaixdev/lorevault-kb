package com.lorevault.api.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
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
    @DisplayName("status update broadcasts without dropping healthy emitters")
    void onStatusUpdateBroadcasts() {
        broadcaster.register();

        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, UUID.randomUUID(), List.of(UUID.randomUUID()));

        broadcaster.onStatusUpdate(event);

        assertThat(broadcaster.getConnectionCount()).isEqualTo(1);
    }
}
