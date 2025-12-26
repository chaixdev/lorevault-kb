package com.lorevault.api.tck.ai;

import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TCK for SceneDetectionPort implementations.
 *
 * Contract focus:
 * - detectScenesInText returns list (may be empty for no scenes)
 * - isAvailable reports service status
 * - getImplementationInfo provides non-null identifier
 * - handles edge cases (null/empty text) gracefully
 */
public abstract class SceneDetectionPortTCK {

    protected abstract SceneDetectionPort createPort();

    @Test
    void detectScenesInText_returns_list_never_null() {
        SceneDetectionPort port = createPort();
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        
        List<SceneWithCoordinates> scenes = port.detectScenesInText(jobId, chapterId, "Some chapter text");
        
        assertThat(scenes).isNotNull();
        // May be empty, but never null
    }

    @Test
    void detectScenesInText_handles_empty_text_gracefully() {
        SceneDetectionPort port = createPort();
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        
        List<SceneWithCoordinates> scenes = port.detectScenesInText(jobId, chapterId, "");
        
        assertThat(scenes).isNotNull();
        // Should handle empty text without throwing
    }

    @Test
    void detectScenesInText_handles_null_text_gracefully() {
        SceneDetectionPort port = createPort();
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        
        List<SceneWithCoordinates> scenes = port.detectScenesInText(jobId, chapterId, null);
        
        assertThat(scenes).isNotNull();
        // Should handle null text without throwing
    }

    @Test
    void isAvailable_returns_boolean() {
        SceneDetectionPort port = createPort();
        
        boolean available = port.isAvailable();
        
        // Just verify it returns without throwing
        assertThat(available).isIn(true, false);
    }

    @Test
    void getImplementationInfo_returns_non_null_identifier() {
        SceneDetectionPort port = createPort();
        
        String info = port.getImplementationInfo();
        
        assertThat(info).isNotNull();
        assertThat(info).isNotBlank();
    }
}
