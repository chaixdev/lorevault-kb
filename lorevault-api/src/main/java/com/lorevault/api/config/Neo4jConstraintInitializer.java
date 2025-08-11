package com.lorevault.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;

/**
 * Ensures minimal Neo4j constraints exist (id uniqueness handled by SDN, we add contentHash uniqueness).
 */
@Configuration
@Slf4j
public class Neo4jConstraintInitializer {

    private static final String CHAPTER_CONTENT_HASH_UNIQUE =
            "CREATE CONSTRAINT chapter_contentHash_unique IF NOT EXISTS FOR (c:Chapter) REQUIRE c.contentHash IS UNIQUE";

    @Bean
    ApplicationRunner neo4jConstraintsRunner(Neo4jClient client) {
        return args -> {
            try {
                client.query(CHAPTER_CONTENT_HASH_UNIQUE).run();
                log.info("Ensured Neo4j uniqueness constraint on Chapter.contentHash");
            } catch (Exception e) {
                log.warn("Failed to create Chapter.contentHash constraint: {}", e.getMessage());
            }
        };    }
}
