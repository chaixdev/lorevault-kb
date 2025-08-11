package com.lorevault.api.infrastructure.config;

import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.infrastructure.ai.openai.OpenAiSceneDetectionAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for ports and adapters.
 * Wires up port implementations based on application properties.
 */
@Configuration
@RequiredArgsConstructor
public class PortsAdaptersConfiguration {
    
    /**
     * Configure the scene detection port implementation.
     * Defaults to OpenAI implementation but can be switched via configuration.
     */
    @Bean
    @ConditionalOnProperty(
        name = "lorevault.ai.scene-detection.provider", 
        havingValue = "openai", 
        matchIfMissing = true  // Default to OpenAI if not specified
    )
    public SceneDetectionPort sceneDetectionPort(OpenAiSceneDetectionAdapter adapter) {
        return adapter;
    }
    
    // Future: Add other implementations
    // @Bean
    // @ConditionalOnProperty(name = "lorevault.ai.scene-detection.provider", havingValue = "local")
    // public SceneDetectionPort localSceneDetectionPort() {
    //     return new LocalSceneDetectionAdapter();
    // }
    
    // @Bean
    // @ConditionalOnProperty(name = "lorevault.ai.scene-detection.provider", havingValue = "mock")
    // public SceneDetectionPort mockSceneDetectionPort() {
    //     return new MockSceneDetectionAdapter();
    // }
}
