package com.lorevault.api.configuration;

import com.lorevault.api.schema.GraphSchemaInitializer;
import com.lorevault.api.schema.SchemaConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Configures and runs schema initialization based on application properties.
 * Disabled by default in production profiles.
 */
@Configuration
@EnableConfigurationProperties(SchemaConfigurationProperties.class)
@Slf4j
@RequiredArgsConstructor
public class SchemaBootstrapConfiguration {

    private final GraphSchemaInitializer graphSchemaInitializer;
    private final SchemaConfigurationProperties schemaProperties;

    @Bean
    @ConditionalOnProperty(
        prefix = "lorevault.schema",
        name = "enabled", 
        havingValue = "true",
        matchIfMissing = false
    )
    @Order(100) // Run after database connectivity is established
    ApplicationRunner schemaBootstrapRunner() {
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
