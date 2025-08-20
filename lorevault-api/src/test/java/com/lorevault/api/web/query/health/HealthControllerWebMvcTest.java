package com.lorevault.api.web.query.health;

import com.lorevault.api.service.system.EmbeddingHealthCheckService;
import com.lorevault.api.service.system.LlmChatSlotsHealthService;
import com.lorevault.api.service.system.LlmHealthCheckService;
import com.lorevault.api.service.system.metrics.HealthMetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    LlmHealthCheckService llmHealthCheckService;
    @MockBean
    LlmChatSlotsHealthService chatSlotsHealthService;
    @MockBean
    EmbeddingHealthCheckService embeddingHealthCheckService;

    @AfterEach
    void tearDown() { reset(llmHealthCheckService, chatSlotsHealthService, embeddingHealthCheckService); }

    @Test
    void getHealth_overallHealthy_returns200WithChecks() throws Exception {
        when(llmHealthCheckService.isLlmServiceHealthy()).thenReturn(true);
        when(chatSlotsHealthService.checkSlots()).thenReturn(Map.of(
                "nlp-small", new HealthMetricsCollector.ModelHealthStatus(true, "small", "OK", 10, 10, 1),
                "nlp-big", new HealthMetricsCollector.ModelHealthStatus(true, "big", "OK", 12, 12, 1)
        ));
        when(embeddingHealthCheckService.getLastStatus()).thenReturn(new EmbeddingHealthCheckService.HealthStatus(true, null, 5, 768));

        mockMvc.perform(get("/api/query/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.checks.llm.healthy").value(true))
                .andExpect(jsonPath("$.checks.embeddings.healthy").value(true))
                .andExpect(jsonPath("$.checks.llm.slots['nlp-small'].healthy").value(true));
    }

    @Test
    void getHealth_unhealthyEmbeddings_includesError() throws Exception {
        when(llmHealthCheckService.isLlmServiceHealthy()).thenReturn(true);
        when(chatSlotsHealthService.checkSlots()).thenReturn(Map.of());
        when(embeddingHealthCheckService.getLastStatus()).thenReturn(new EmbeddingHealthCheckService.HealthStatus(false, "dim mismatch", 7, 0));

        mockMvc.perform(get("/api/query/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(false))
                .andExpect(jsonPath("$.checks.embeddings.error").value("dim mismatch"));
    }

    @Test
    void getLlmHealth_mixedModelsAndSlots() throws Exception {
        when(llmHealthCheckService.checkAllModels()).thenReturn(Map.of(
                "model-A", new HealthMetricsCollector.ModelHealthStatus(true, "A", "OK", 5, 5, 1),
                "model-B", new HealthMetricsCollector.ModelHealthStatus(false, "B", "timeout", 50, 50, 3)
        ));
        when(chatSlotsHealthService.checkSlots()).thenReturn(Map.of(
                "nlp-small", new HealthMetricsCollector.ModelHealthStatus(true, "A", "OK", 8, 8, 1),
                "nlp-big", new HealthMetricsCollector.ModelHealthStatus(false, "B", "timeout", 60, 60, 2)
        ));

        mockMvc.perform(get("/api/query/health/llm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(false))
                .andExpect(jsonPath("$.models['model-A'].healthy").value(true))
                .andExpect(jsonPath("$.models['model-B'].healthy").value(false))
                .andExpect(jsonPath("$.slots['nlp-big'].status").value("error"));
    }

    @Test
    void getEmbeddingHealth_unhealthy_includesError() throws Exception {
        when(embeddingHealthCheckService.checkEmbeddingService()).thenReturn(new EmbeddingHealthCheckService.HealthStatus(false, "boom", 9, 0));

        mockMvc.perform(get("/api/query/health/embeddings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(false))
                .andExpect(jsonPath("$.error").value("boom"));
    }
}
