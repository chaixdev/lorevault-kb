package com.lorevault.api.infrastructure.graph;

import com.lorevault.api.test.Neo4jIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify Neo4j Testcontainer setup works.
 */
class Neo4jConnectionTest extends Neo4jIntegrationTestBase {

    @Autowired
    private Neo4jClient neo4jClient;

    @Test
    void shouldConnectToNeo4j() {
        // When/Then - just verify we can execute a simple query
        var result = neo4jClient
            .query("RETURN 1 AS number")
            .fetchAs(Integer.class)
            .one();
        
        assertThat(result).contains(1);
    }
}
