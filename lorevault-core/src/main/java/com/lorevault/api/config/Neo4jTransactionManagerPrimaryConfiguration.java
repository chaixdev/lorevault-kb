package com.lorevault.api.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Ensures the Neo4j transaction manager is @Primary when multiple
 * PlatformTransactionManager beans exist (e.g., when the catalog module
 * provides a JDBC transaction manager for PostgreSQL).
 *
 * Without @Primary, Spring cannot resolve which TransactionManager to use
 * for TransactionOperations, causing Neo4jTemplate.txTemplate to be null.
 *
 * Neo4j is the primary database — its transaction manager should be the default.
 * The catalog's JDBC transaction manager is explicitly qualified via
 * @Transactional(transactionManager = "catalogTransactionManager").
 */
@Configuration
@Slf4j
public class Neo4jTransactionManagerPrimaryConfiguration {

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            Driver driver,
            DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "lorevault.schema",
        name = "enabled", 
        havingValue = "true",
        matchIfMissing = false
    )
    @Order(100) // Run after database connectivity is established
    ApplicationRunner schemaBootstrapRunner(
            GraphSchemaInitializer graphSchemaInitializer,
            SchemaConfigurationProperties schemaProperties) {
        return args -> {
            var mode = schemaProperties.mode();
            if (mode == null) {
                mode = SchemaConfigurationProperties.Mode.ENSURE;
            }

            log.info("Schema initialization starting: mode={}, backend={}", 
                mode, schemaProperties.backend());

            try {
                switch (mode) {
                    case ENSURE -> {
                        graphSchemaInitializer.ensureMinimalSchema();
                        log.info("Schema initialization completed successfully");
                    }
                    case VALIDATE -> {
                        var report = graphSchemaInitializer.validateMinimalSchema();
                        log.info("Schema validation: {}", report.summary());
                    }
                    case NONE -> {
                        log.debug("Schema initialization skipped (mode=NONE)");
                    }
                }
            } catch (Exception e) {
                if (schemaProperties.failOnError()) {
                    log.error("Schema initialization failed and failOnError=true", e);
                    throw new RuntimeException("Schema initialization failed", e);
                } else {
                    log.warn("Schema initialization failed but continuing (failOnError=false): {}", 
                        e.getMessage());
                }
            }
        };
    }
}