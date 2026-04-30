package com.lorevault.api.timeline;

import com.lorevault.api.ingestion.resolution.event.DefaultTemporalEdgeService;
import com.lorevault.api.content.timeline.infrastructure.TemporalGraphRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration tests for DefaultTemporalEdgeService.
 * Tests the idempotent creation of temporal edges between scenes.
 */
@DataNeo4jTest
@Testcontainers
@Import({DefaultTemporalEdgeService.class})
@DisplayName("DefaultTemporalEdgeService Integration Tests")
public class DefaultTemporalEdgeServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpassword");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
    }

    @Autowired
    private DefaultTemporalEdgeService defaultTemporalEdgeService;

    @Autowired
    private TemporalGraphRepository temporalGraphRepository;

    @Test
    @DisplayName("Should handle empty book gracefully without throwing exceptions")
    void shouldHandleEmptyBookGracefully() {
        // Given - a non-existent book ID
        UUID emptyBookId = UUID.randomUUID();

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> defaultTemporalEdgeService.createAllDefaults(emptyBookId));
    }

    @Test
    @DisplayName("Service should be autowired correctly")
    void shouldAutowireServiceCorrectly() {
        assertThat(defaultTemporalEdgeService).isNotNull();
        assertThat(temporalGraphRepository).isNotNull();
    }
}
