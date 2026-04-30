package com.lorevault.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.lorevault.api.testing.TestImages;
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

@SpringBootTest
@Testcontainers
@Tag("integration")
class Neo4jSchemaInitializerBookObjectIndexesIT {

    @Container
    @SuppressWarnings("resource")
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpassword");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
        registry.add("lorevault.system.health.startup.enabled", () -> "false");
        registry.add("lorevault.llm.health.enabled", () -> "false");
        registry.add("lorevault.embedding.health.enabled", () -> "false");
    }

    @Autowired
    private Neo4jSchemaInitializer schemaInitializer;

    @Autowired
    private Neo4jClient neo4jClient;

    @Test
    void ensuresBookObjectConstraintsAndIndex() {
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());

        assertThat(constraintExists("book_object_id_unique")).isTrue();
        assertThat(constraintExists("book_object_scope_unique")).isTrue();
        assertThat(indexExists("book_object_book_name")).isTrue();
    }

    private boolean indexExists(String indexName) {
        return neo4jClient.query(
                        "SHOW INDEXES YIELD name WHERE name = $indexName RETURN count(*) > 0 as exists")
                .bind(indexName).to("indexName")
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);
    }

    private boolean constraintExists(String constraintName) {
        return neo4jClient.query(
                        "SHOW CONSTRAINTS YIELD name WHERE name = $constraintName RETURN count(*) > 0 as exists")
                .bind(constraintName).to("constraintName")
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);
    }
}
