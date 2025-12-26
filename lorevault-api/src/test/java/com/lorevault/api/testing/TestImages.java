package com.lorevault.api.testing;

/**
 * Centralized definitions for container image tags used in tests.
 *
 * This prevents drift across different tests and aligns with docker-compose defaults.
 */
public final class TestImages {

    /**
     * Neo4j image tag to use for Testcontainers in tests.
     * Defaults to "neo4j:5.26" to match docker-compose.yml.
     * Can be overridden via system property or environment variable "TEST_NEO4J_IMAGE".
     */
    public static final String NEO4J_IMAGE = System.getProperty(
            "TEST_NEO4J_IMAGE",
            System.getenv().getOrDefault("TEST_NEO4J_IMAGE", "neo4j:5.26")
    );

    private TestImages() {
        // no instances
    }
}
