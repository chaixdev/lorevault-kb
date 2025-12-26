package com.lorevault.api.infrastructure.ai.openai;

import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.retry.RetryAwareSceneDetectionService;
import com.lorevault.api.tck.ai.SceneDetectionPortTCK;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TCK for OpenAiSceneDetectionAdapter using mocked service.
 */
@ExtendWith(MockitoExtension.class)
public class OpenAiSceneDetectionAdapterTckTest extends SceneDetectionPortTCK {

    @Mock private RetryAwareSceneDetectionService mockRetryService;
    private OpenAiSceneDetectionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiSceneDetectionAdapter(mockRetryService);
        
        // Mock successful scene detection response
        SceneWithCoordinates mockScene = new SceneWithCoordinates(
            1,
            0L,
            100L,
            "First scene with character introduction"
        );
        
        lenient().when(mockRetryService.detectScenesWithRetry(any(), any(UUID.class), anyString()))
            .thenReturn(List.of(mockScene));
    }

    @Override
    protected SceneDetectionPort createPort() {
        return adapter;
    }
}
