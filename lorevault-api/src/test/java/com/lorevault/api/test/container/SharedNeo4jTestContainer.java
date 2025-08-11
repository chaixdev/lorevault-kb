package com.lorevault.api.test.container;

import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Neo4j Testcontainer for all integration tests.
 * Ensures only one container is started per JVM.
 */
public final class SharedNeo4jTestContainer {

    private static final Neo4jContainer<?> INSTANCE = build();
    private static volatile boolean started = false;

    private SharedNeo4jTestContainer() {}

    private static Neo4jContainer<?> build() {
        return new Neo4jContainer<>(DockerImageName.parse("neo4j:5.19.0"))
                .withEnv("NEO4J_AUTH", "neo4j/password")
                .withReuse(true)
                .withStartupAttempts(3);
    }

    public static Neo4jContainer<?> getInstance() {
        if (!started) {
            synchronized (SharedNeo4jTestContainer.class) {
                if (!started) {
                    INSTANCE.start();
                    started = true;
                }
            }
        }
        return INSTANCE;
    }
}
