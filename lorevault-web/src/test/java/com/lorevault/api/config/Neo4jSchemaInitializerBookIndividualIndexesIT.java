package com.lorevault.api.config;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@Testcontainers
@Tag("integration")
class Neo4jSchemaInitializerBookIndividualIndexesIT {

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
    void ensuresBookIndividualConstraintsAndIndex() {
        assertDoesNotThrow(() -> schemaInitializer.ensureMinimalSchema());

        assertThat(constraintExists("book_individual_id_unique")).isTrue();
        assertThat(constraintExists("book_individual_scope_unique")).isTrue();
    }

    private boolean constraintExists(String constraintName) {
        return neo4jClient.query("SHOW CONSTRAINTS YIELD name WHERE name = $constraintName RETURN count(*) > 0 as exists")
                .bind(constraintName).to("constraintName")
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);
    }
}
