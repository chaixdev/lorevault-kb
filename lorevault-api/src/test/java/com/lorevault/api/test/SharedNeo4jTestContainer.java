package com.lorevault.api.test;

import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Neo4j Testcontainer shared across all integration tests to avoid
 * repeated image pulls, startup latency, and port churn that invalidates
 * driver sessions between Spring contexts.
 */
public final class SharedNeo4jTestContainer {
    private static final Neo4jContainer<?> INSTANCE = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.19.0"))
            .withEnv("NEO4J_AUTH", "neo4j/password")
            .withReuse(true)
            .withStartupAttempts(3);

    static {
        INSTANCE.start();
    }

    private SharedNeo4jTestContainer() {}

    public static Neo4jContainer<?> getInstance() {
        return INSTANCE;
    }
}
