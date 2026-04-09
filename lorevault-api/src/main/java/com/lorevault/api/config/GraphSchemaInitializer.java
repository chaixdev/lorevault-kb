package com.lorevault.api.config;

/**
 * Contract for graph database schema initialization.
 * Implementations should be idempotent and lenient (non-fatal on failure).
 */
public interface GraphSchemaInitializer {

    /**
     * Ensures minimal schema artifacts exist in the graph database.
     * This method should be idempotent and not fail the application startup on errors.
     */
    void ensureMinimalSchema();

    /**
     * Validates that minimal schema artifacts exist.
     * 
     * @return report indicating which artifacts exist/missing
     */
    SchemaReport validateMinimalSchema();

    /**
     * Simple report of schema validation results.
     */
    record SchemaReport(
        boolean hasAllConstraints,
        boolean hasAllIndexes,
        String summary
    ) {}
}
