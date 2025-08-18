package com.lorevault.api.infrastructure.graph;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Neo4jSchemaInitializer using Testcontainers.
 * Uses a minimal Spring context with just Neo4j and schema components.
 */
@SpringBootTest(classes = {
    Neo4jSchemaInitializer.class
})
@TestPropertySource(properties = {
    "lorevault.schema.enabled=true", 
    "lorevault.schema.mode=ensure",
    "spring.main.allow-bean-definition-overriding=true"
})
@Testcontainers
class Neo4jSchemaInitializerIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.23")
            .withAdminPassword("password")
            .withReuse(true);

    @DynamicPropertySource
    static void configureNeo4j(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
    }

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private Neo4jSchemaInitializer schemaInitializer;

    @Test
    void ensureMinimalSchema_shouldCreateConstraintsAndIndexes() {
        // When
        schemaInitializer.ensureMinimalSchema();
        
        // Then - verify constraints exist
        Collection<Map<String, Object>> constraints = neo4jClient
            .query("SHOW CONSTRAINTS")
            .fetch()
            .all();
            
        assertThat(constraints).isNotEmpty();
        
        List<String> constraintNames = constraints.stream()
            .map(c -> (String) c.get("name"))
            .toList();
            
        // Check for at least some key constraints
        assertThat(constraintNames).containsAnyOf(
            "chapter_id_unique",
            "scene_id_unique", 
            "chunk_id_unique"
        );

        // Verify indexes exist
        Collection<Map<String, Object>> indexes = neo4jClient
            .query("SHOW INDEXES")
            .fetch()
            .all();
            
        assertThat(indexes).isNotEmpty();
    }
    
    @Test
    void ensureMinimalSchema_shouldBeIdempotent() {
        // When - run twice
        schemaInitializer.ensureMinimalSchema();
        schemaInitializer.ensureMinimalSchema();
        
        // Then - should not fail and constraints should still exist
        Collection<Map<String, Object>> constraints = neo4jClient
            .query("SHOW CONSTRAINTS")
            .fetch()
            .all();
            
        assertThat(constraints).hasSizeGreaterThan(0);
    }
}
