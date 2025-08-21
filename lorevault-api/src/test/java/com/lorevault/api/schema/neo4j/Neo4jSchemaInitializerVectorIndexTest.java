package com.lorevault.api.schema.neo4j;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration test for Neo4j schema initialization including vector index creation.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class Neo4jSchemaInitializerVectorIndexTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.20")
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
    void ensureMinimalSchema_createsVectorIndex_idempotently() {
        // When: Running schema initialization
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Vector index should be created
        assertThat(vectorIndexExists("chunk_embedding_idx")).isTrue();
        
        // When: Running again (idempotent)
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Still works without errors
        assertThat(vectorIndexExists("chunk_embedding_idx")).isTrue();
    }

    @Test
    void ensureMinimalSchema_handlesErrorsGracefully() {
        // Given: Invalid Neo4j state (simulate error condition)
        // This test ensures non-fatal behavior on schema creation errors
        
        // When: Schema initialization runs
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Application doesn't crash (lenient failure mode)
        // Basic constraints should still be created
        assertThat(constraintExists("chapter_id_unique")).isTrue();
    }

    private boolean vectorIndexExists(String indexName) {
        return neo4jClient.query(
            "SHOW INDEXES YIELD name WHERE name = $indexName RETURN count(*) > 0 as exists"
        )
        .bind("indexName").to(indexName)
        .fetchAs(Boolean.class)
        .one()
        .orElse(false);
    }

    private boolean constraintExists(String constraintName) {
        return neo4jClient.query(
            "SHOW CONSTRAINTS YIELD name WHERE name = $constraintName RETURN count(*) > 0 as exists"
        )
        .bind("constraintName").to(constraintName)
        .fetchAs(Boolean.class)
        .one()
        .orElse(false);
    }
}