package com.lorevault.api.infrastructure.config;

import com.lorevault.api.application.port.SceneDetectionPort;
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
}
