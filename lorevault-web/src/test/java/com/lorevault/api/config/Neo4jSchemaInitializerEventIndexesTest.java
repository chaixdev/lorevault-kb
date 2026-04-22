package com.lorevault.api.config;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration test for Neo4j schema initialization focusing on Event constraints and indexes.
 * Verifies that Event identity uniqueness and per-chapter ordering indexes are created correctly.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class Neo4jSchemaInitializerEventIndexesTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpassword");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
    }

    @Autowired
    private Neo4jSchemaInitializer schemaInitializer;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void setUp() {
        // Clean slate: drop existing indexes and constraints
        neo4jClient.query("CALL apoc.schema.assert({}, {}, true)").run();
    }

    @Test
    void ensuresEventIdentityAndPerChapterIndex() {
        // When: Running schema initialization
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Event constraints and indexes should be created
        assertThat(constraintExists("event_id_unique")).isTrue();
        assertThat(indexExists("event_per_chapter_scene_idx")).isTrue();
        
        // When: Running again (idempotent)
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Still works without errors
        assertThat(constraintExists("event_id_unique")).isTrue();
        assertThat(indexExists("event_per_chapter_scene_idx")).isTrue();
    }

    @Test
    void ensureMinimalSchema_createsAllBasicConstraints() {
        // When: Running schema initialization
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Basic constraints should still exist alongside Event constraints
        assertThat(constraintExists("chapter_id_unique")).isTrue();
        assertThat(constraintExists("scene_id_unique")).isTrue();
        assertThat(constraintExists("chunk_id_unique")).isTrue();
        assertThat(constraintExists("event_id_unique")).isTrue();
    }

    private boolean indexExists(String indexName) {
        return neo4jClient.query(
                "SHOW INDEXES YIELD name WHERE name = $indexName RETURN count(*) > 0 as exists")
            .bind("indexName").to(indexName)
            .fetchAs(Boolean.class)
            .one()
            .orElse(false);
    }

    private boolean constraintExists(String constraintName) {
        return neo4jClient.query(
                "SHOW CONSTRAINTS YIELD name WHERE name = $constraintName RETURN count(*) > 0 as exists")
            .bind("constraintName").to(constraintName)
            .fetchAs(Boolean.class)
            .one()
            .orElse(false);
    }
}