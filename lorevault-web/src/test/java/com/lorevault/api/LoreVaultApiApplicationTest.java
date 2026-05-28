package com.lorevault.api;

import com.lorevault.api.integration.TestConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test that verifies the full Spring application context loads
 * successfully with all bean wiring intact.
 *
 * <p>Requires running dev infrastructure:
 * <ul>
 *   <li>Neo4j — {@code docker-compose up -d neo4j} (auth: neo4j/neosecret)</li>
 *   <li>Postgres — {@code docker-compose up -d postgres} (catalog DB)</li>
 * </ul>
 *
 * <p>Tagged as {@code integration} so it is excluded from the default
 * {@code mvn test} run — execute via {@code mvn verify -P integration-tests}.
 *
 * <p>The {@code test} profile disables Spring AI auto-configuration
 * ({@code @Profile("!test")}), and {@link TestConfig} provides mock
 * {@code ChatClient} and {@code EmbeddingModel} beans. The
 * {@code @TestPropertySource} overrides the Neo4j password from the
 * test {@code application.properties} (which uses a placeholder value)
 * to match the dev container's actual credentials.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@TestPropertySource(properties = {
        "spring.neo4j.authentication.password=neosecret"
})
@Tag("integration")
@DisplayName("Application context loads")
class LoreVaultApiApplicationTest {

    @Test
    @DisplayName("contextLoads — full Spring context starts without errors")
    void contextLoads() {
        // If we get here, the context loaded successfully.
        // All beans were created, dependencies injected, and
        // startup validation (e.g. StageDispatcher handler map) passed.
    }
}
