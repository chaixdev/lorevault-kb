package com.lorevault.api.infrastructure.config;

import com.lorevault.api.application.port.JobContextPort;
import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.infrastructure.adapter.ThreadLocalJobContextAdapter;
import com.lorevault.api.infrastructure.ai.openai.OpenAiSceneDetectionAdapter;
import lombok.RequiredArgsConstructor;
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
     * Currently uses OpenAI implementation with enhanced retry handling.
     * Multi-provider support will be added after properties to YAML migration.
     */
    @Bean
    public SceneDetectionPort sceneDetectionPort(OpenAiSceneDetectionAdapter adapter) {
        return adapter;
    }

    /**
     * Configure the semantic search port implementation.
     * 
     * Default: Neo4j-native vector search (lorevault.search.provider=neo4j)
     * Fallback: In-memory cosine similarity (lorevault.search.provider=memory)
     * 
     * Neo4jSemanticSearchAdapter and InMemorySemanticSearchAdapter are conditionally
     * registered as @Component beans based on the property value.
     */
    // No longer needed - adapters self-register via @ConditionalOnProperty
    
    /**
     * Configure the job context port implementation.
     * Uses thread-local storage for job ID management.
     */
    @Bean
    public JobContextPort jobContextPort(ThreadLocalJobContextAdapter adapter) {
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
