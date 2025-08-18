package com.lorevault.api.schema;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for schema initialization behavior.
 */
@ConfigurationProperties(prefix = "lorevault.schema")
public record SchemaConfigurationProperties(
    /**
     * Schema initialization mode.
     * - none: Skip schema initialization completely
     * - ensure: Create missing schema artifacts (default in dev)
     * - validate: Only validate and report, don't create
     */
    Mode mode,
    
    /**
     * Graph database backend type.
     */
    Backend backend,
    
    /**
     * Whether schema initialization is enabled at all.
     */
    boolean enabled,
    
    /**
     * Whether to fail startup on schema errors.
     * Currently always false (lenient mode).
     */
    boolean failOnError
) {
    
    public enum Mode {
        NONE, ENSURE, VALIDATE
    }
    
    public enum Backend {
        NEO4J
        // Future: PGVECTOR
    }
    
    /**
     * Provides default values for development.
     */
    public static SchemaConfigurationProperties defaults() {
        return new SchemaConfigurationProperties(
            Mode.ENSURE,
            Backend.NEO4J,
            true,
            false
        );
    }
}
