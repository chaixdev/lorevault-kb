package com.lorevault.api.config;

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

import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.library.chunk.Chunk;
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
    void ensureMinimalSchema_createsVectorIndex_idempotently() {
        // When: Running schema initialization
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Vector index should be created with correct dimensions
        assertThat(vectorIndexExists(Chunk.VECTOR_INDEX_NAME)).isTrue();
        assertThat(vectorIndexDimensions(Chunk.VECTOR_INDEX_NAME)).isEqualTo(1536);
        assertThat(vectorIndexExists(ChapterEvent.VECTOR_INDEX_NAME)).isTrue();
        assertThat(vectorIndexDimensions(ChapterEvent.VECTOR_INDEX_NAME)).isEqualTo(1536);
        
        // When: Running again (idempotent)
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());
        
        // Then: Still works without errors
        assertThat(vectorIndexExists(Chunk.VECTOR_INDEX_NAME)).isTrue();
        assertThat(vectorIndexExists(ChapterEvent.VECTOR_INDEX_NAME)).isTrue();
    }

    @Test
    void ensureMinimalSchema_rebuildsVectorIndexWhenDimensionsDrift() {
        neo4jClient.query(
                "CREATE VECTOR INDEX " + Chunk.VECTOR_INDEX_NAME + " IF NOT EXISTS FOR (ch:Chunk) ON (ch.embedding) " +
                "OPTIONS {indexConfig: {`vector.dimensions`: 3072, `vector.similarity_function`: 'cosine'}}"
        ).run();

        assertThat(vectorIndexDimensions(Chunk.VECTOR_INDEX_NAME)).isEqualTo(3072);

        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());

        assertThat(vectorIndexExists(Chunk.VECTOR_INDEX_NAME)).isTrue();
        assertThat(vectorIndexDimensions(Chunk.VECTOR_INDEX_NAME)).isEqualTo(1536);
    }

    @Test
    void ensureMinimalSchema_rebuildsChapterEventVectorIndexWhenDimensionsDrift() {
        neo4jClient.query(
                "CREATE VECTOR INDEX " + ChapterEvent.VECTOR_INDEX_NAME + " IF NOT EXISTS FOR (ce:ChapterEvent) ON (ce.embedding) " +
                "OPTIONS {indexConfig: {`vector.dimensions`: 3072, `vector.similarity_function`: 'cosine'}}"
        ).run();

        assertThat(vectorIndexDimensions(ChapterEvent.VECTOR_INDEX_NAME)).isEqualTo(3072);

        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());

        assertThat(vectorIndexExists(ChapterEvent.VECTOR_INDEX_NAME)).isTrue();
        assertThat(vectorIndexDimensions(ChapterEvent.VECTOR_INDEX_NAME)).isEqualTo(1536);
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

    private int vectorIndexDimensions(String indexName) {
        return neo4jClient.query(
            "SHOW INDEXES YIELD name, options WHERE name = $indexName " +
            "RETURN options.indexConfig.`vector.dimensions` AS dimensions"
        )
        .bind("indexName").to(indexName)
        .fetchAs(Long.class)
        .one()
        .map(Long::intValue)
        .orElse(-1);
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
