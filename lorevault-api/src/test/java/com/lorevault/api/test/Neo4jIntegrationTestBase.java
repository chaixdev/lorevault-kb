package com.lorevault.api.test;

import com.lorevault.api.test.container.SharedNeo4jTestContainer;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;

/**
 * Base class for Neo4j integration tests that only loads Neo4j Spring Data components.
 * Uses the shared Neo4j Testcontainer to avoid container startup overhead.
 */
@DataNeo4jTest
public abstract class Neo4jIntegrationTestBase {

    private static final Neo4jContainer<?> neo4j = SharedNeo4jTestContainer.getInstance();

    @DynamicPropertySource
    static void configureNeo4j(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
    }
}
