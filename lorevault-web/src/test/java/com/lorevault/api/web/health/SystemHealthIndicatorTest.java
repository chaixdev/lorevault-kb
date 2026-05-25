package com.lorevault.api.web.health;

import com.lorevault.api.config.ModelSlot;
import com.lorevault.api.health.HealthMetricsCollector;
import com.lorevault.api.health.SystemHealthService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SystemHealthIndicatorTest {

    @Test
    void health_mapsDetailsAndStatus() {
        var svc = Mockito.mock(SystemHealthService.class);
        var indicator = new SystemHealthIndicator(svc);

        var llmHealth = new HealthMetricsCollector.ModelHealthStatus(true, "gemini-2.5-flash-lite", "OK", 10, 10, 1);
        var emb = new SystemHealthService.EmbeddingHealthStatus(false, "dim mismatch", 5, 0);
        var db = new SystemHealthService.DatabaseHealthStatus(false, "db down", 3);
        var slots = Map.of(
                ModelSlot.NLP_SMALL.slotName(), new HealthMetricsCollector.ModelHealthStatus(true, "A", "OK", 8, 8, 1),
                ModelSlot.NLP_BIG.slotName(), new HealthMetricsCollector.ModelHealthStatus(false, "B", "timeout", 60, 60, 2)
        );
        var system = new SystemHealthService.SystemHealthResponse(false, llmHealth, emb, slots, db);
        when(svc.getOverallSystemHealth()).thenReturn(system);

        Health h = indicator.health();
        assertThat(h.getStatus().getCode()).isEqualTo("DOWN");
    assertThat(h.getDetails()).containsKeys("llm", "embeddings", "database");

    @SuppressWarnings("unchecked")
    Map<String, Object> llm = (Map<String, Object>) h.getDetails().get("llm");
    assertThat(llm.get("healthy")).isEqualTo(true);

    @SuppressWarnings("unchecked")
    Map<String, Object> embeddings = (Map<String, Object>) h.getDetails().get("embeddings");
    assertThat(embeddings.get("healthy")).isEqualTo(false);
    assertThat(embeddings.get("error")).isEqualTo("dim mismatch");

    @SuppressWarnings("unchecked")
    Map<String, Object> database = (Map<String, Object>) h.getDetails().get("database");
    assertThat(database.get("healthy")).isEqualTo(false);
    assertThat(database.get("error")).isEqualTo("db down");
    }
}
