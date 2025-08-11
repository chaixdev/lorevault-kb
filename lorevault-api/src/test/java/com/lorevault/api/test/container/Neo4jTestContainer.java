package com.lorevault.api.test.container;

import org.testcontainers.containers.Neo4jContainer;

public final class Neo4jTestContainer {
    @SuppressWarnings("resource")
    private static final Neo4jContainer<?> INSTANCE = new Neo4jContainer<>("neo4j:5.19.0")
            .withEnv("NEO4J_AUTH", "neo4j/password")
            .withReuse(true);
    static { INSTANCE.start(); }
    private Neo4jTestContainer() {}
    public static Neo4jContainer<?> getInstance() { return INSTANCE; }
}
