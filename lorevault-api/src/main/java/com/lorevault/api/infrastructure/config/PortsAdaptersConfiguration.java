package com.lorevault.api.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for ports and adapters.
 * 
 * Most port implementations are now @Service or @Component annotated and
 * auto-registered by Spring component scanning:
 * - SceneDetectionPort: RetryAwareSceneDetectionService (@Service)
 * - SemanticSearchPort: Neo4jSemanticSearchAdapter or InMemorySemanticSearchAdapter (@ConditionalOnProperty)
 * - ContentPersistencePort: Neo4jContentRepository (@Repository)
 * - etc.
 */
@Configuration
@RequiredArgsConstructor
public class PortsAdaptersConfiguration {
    // Port implementations are auto-registered via component scanning
}
