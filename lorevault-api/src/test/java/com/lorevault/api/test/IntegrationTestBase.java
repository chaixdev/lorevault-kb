package com.lorevault.api.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import com.lorevault.api.test.config.MockLlmConfig;
import com.lorevault.api.test.container.SharedNeo4jTestContainer;
import org.testcontainers.containers.Neo4jContainer;

@SpringBootTest
@Import(MockLlmConfig.class)
public abstract class IntegrationTestBase {

    private static final Neo4jContainer<?> neo4j = SharedNeo4jTestContainer.getInstance();

    @DynamicPropertySource
    static void registerNeo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
    }
}
