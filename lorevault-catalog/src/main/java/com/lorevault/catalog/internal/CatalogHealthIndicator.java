package com.lorevault.catalog.internal;

import java.util.Map;


import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the catalog PostgreSQL database.
 * <p>
 * Surfaces at {@code /actuator/health} as a {@code catalog} component.
 * Runs a lightweight {@code SELECT 1} to verify connectivity. When
 * the catalog is disabled ({@code lorevault.catalog.enabled=false}),
 * this bean is not created and no catalog health entry appears.
 */
@Component
@ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "true")
public class CatalogHealthIndicator implements HealthIndicator {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    CatalogHealthIndicator(NamedParameterJdbcTemplate catalogNamedParameterJdbcTemplate) {
        this.jdbcTemplate = catalogNamedParameterJdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Map.of(), Integer.class);
            return Health.up()
                    .withDetail("datasource", "reachable")
                    .build();
        } catch (DataAccessException e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
