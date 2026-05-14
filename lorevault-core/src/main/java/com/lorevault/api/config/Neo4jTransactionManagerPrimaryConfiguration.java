package com.lorevault.api.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
public class Neo4jTransactionManagerPrimaryConfiguration {

    @Bean
    @Primary
    public PlatformTransactionManager neo4jTransactionManager(
            Driver driver,
            DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}