package com.lorevault.api.web.query.health;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.health.SystemHealthService;
import com.lorevault.api.health.HealthMetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
class HealthControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SystemHealthService systemHealthService;

    @AfterEach
    void tearDown() { reset(systemHealthService); }

    @Test
    void getHealth_overallHealthy_returns200WithChecks() throws Exception {
        var llmHealth = new HealthMetricsCollector.ModelHealthStatus(true, "gemini-2.5-flash-lite", "OK", 10, 10, 1);
        var embeddingHealth = new SystemHealthService.EmbeddingHealthStatus(true, null, 5, 768);
        var databaseHealth = new SystemHealthService.DatabaseHealthStatus(true, null, 4);
        var chatSlotsHealth = Map.of(
                "nlp-small", new HealthMetricsCollector.ModelHealthStatus(true, "small", "OK", 10, 10, 1),
                "nlp-big", new HealthMetricsCollector.ModelHealthStatus(true, "big", "OK", 12, 12, 1)
        );
        var systemHealth = new SystemHealthService.SystemHealthResponse(true, llmHealth, embeddingHealth, chatSlotsHealth, databaseHealth);
        
        when(systemHealthService.getOverallSystemHealth()).thenReturn(systemHealth);

        mockMvc.perform(get("/api/query/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.checks.llm.healthy").value(true))
                .andExpect(jsonPath("$.checks.embeddings.healthy").value(true))
                .andExpect(jsonPath("$.checks.database.healthy").value(true))
                .andExpect(jsonPath("$.checks.llm.slots['nlp-small'].healthy").value(true));
    }

    @Test
        void getHealth_unhealthyEmbeddings_includesError() throws Exception {
        var llmHealth = new HealthMetricsCollector.ModelHealthStatus(true, "gemini-2.5-flash-lite", "OK", 10, 10, 1);
        var embeddingHealth = new SystemHealthService.EmbeddingHealthStatus(false, "dim mismatch", 7, 0);
        var databaseHealth = new SystemHealthService.DatabaseHealthStatus(false, "db unavailable", 9);
        var chatSlotsHealth = Map.<String, HealthMetricsCollector.ModelHealthStatus>of();
        var systemHealth = new SystemHealthService.SystemHealthResponse(false, llmHealth, embeddingHealth, chatSlotsHealth, databaseHealth);
        
        when(systemHealthService.getOverallSystemHealth()).thenReturn(systemHealth);

        mockMvc.perform(get("/api/query/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(false))
                .andExpect(jsonPath("$.checks.embeddings.error").value("dim mismatch"))
                .andExpect(jsonPath("$.checks.database.error").value("db unavailable"));
    }
}
