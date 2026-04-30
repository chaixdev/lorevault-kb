package com.lorevault.api.health;

import com.lorevault.api.config.LoreVaultModelsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Test suite for SystemHealthService.
 * Simplified to focus on business behavior and integration rather than complex mocking.
 * Tests the consolidated health checking capabilities using realistic scenarios.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("SystemHealthService")
class SystemHealthServiceTest {

    @Mock
    private RetryableHealthChecker retryableHealthChecker;

    @Mock
    private ModelHealthValidator modelHealthValidator;

    @Mock
    private HealthMetricsCollector healthMetricsCollector;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    @Qualifier("nlpSmall")
    private ChatClient nlpSmallChatClient;

    @Mock
    @Qualifier("nlpBig")
    private ChatClient nlpBigChatClient;

    @Mock
    private Driver neo4jDriver;

    @Mock
    private LoreVaultModelsProperties modelsProperties;

    @Mock
    private ModelRegistryService modelRegistryService;

    @InjectMocks
    private SystemHealthService service;

    private static final String TEST_MODEL_ID = "gemini-2.5-flash-lite";

    @BeforeEach
    void setUp() {
        // Set up basic configuration
        ReflectionTestUtils.setField(service, "healthEnabled", true);
        ReflectionTestUtils.setField(service, "embeddingHealthEnabled", true);
        ReflectionTestUtils.setField(service, "embeddingTestText", "health_check");
        ReflectionTestUtils.setField(service, "embeddingExpectedDim", null);
        
        // Mock basic model properties - only when needed
        var nlpSmallProps = new LoreVaultModelsProperties.ModelProperties(
            "openai", "http://localhost", "/chat/completions", "key", "gemma-3-4b-it", 0.3, 1.0, 128000);
        var nlpBigProps = new LoreVaultModelsProperties.ModelProperties(
            "openai", "http://localhost", "/chat/completions", "key", TEST_MODEL_ID, 0.3, 1.0, 128000);
            
        // Use lenient() to avoid unnecessary stubbing issues when not all mocks are used in every test
        lenient().when(modelsProperties.nlpSmall()).thenReturn(nlpSmallProps);
        lenient().when(modelsProperties.nlpBig()).thenReturn(nlpBigProps);
        lenient().when(modelRegistryService.getCurrentModelId()).thenReturn(TEST_MODEL_ID);
    }

    @Test
    @DisplayName("should check LLM health successfully")
    void shouldCheckLlmHealthSuccessfully() {
        // Given - mock the complete retry chain to return healthy result
        var healthStatus = new HealthMetricsCollector.ModelHealthStatus(
            true, TEST_MODEL_ID, "OK", 100L, 100L, 1);
        mockSuccessfulRetryChain(healthStatus);

        // When
        var result = service.checkLlmHealth();

        // Then
        assertThat(result.isHealthy()).isTrue();
        assertThat(result.getModelName()).isEqualTo(TEST_MODEL_ID);
    }

    @Test
    @DisplayName("should check LLM health failure")
    void shouldCheckLlmHealthFailure() {
        // Given - mock the complete retry chain to return unhealthy result
        var healthStatus = new HealthMetricsCollector.ModelHealthStatus(
            false, TEST_MODEL_ID, "Connection failed", 100L, 100L, 3);
        mockFailedRetryChain(healthStatus);

        // When
        var result = service.checkLlmHealth();

        // Then
        assertThat(result.isHealthy()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Connection failed");
    }

    @Test
    @DisplayName("should check embedding health successfully")
    void shouldCheckEmbeddingHealthSuccessfully() {
        // Given
        float[] testVector = new float[384];
        when(embeddingModel.embed("health_check")).thenReturn(testVector);
        when(embeddingModel.dimensions()).thenReturn(384);

        // When
        var result = service.checkEmbeddingHealth();

        // Then
        assertThat(result.healthy()).isTrue();
        assertThat(result.dimension()).isEqualTo(384);
        assertThat(result.error()).isNull();
    }

    @Test
    @DisplayName("should check embedding health failure")
    void shouldCheckEmbeddingHealthFailure() {
        // Given
        when(embeddingModel.embed("health_check")).thenThrow(new RuntimeException("Connection failed"));

        // When
        var result = service.checkEmbeddingHealth();

        // Then
        assertThat(result.healthy()).isFalse();
        assertThat(result.error()).isEqualTo("Connection failed");
    }

    @Test
    @DisplayName("should check chat slots health for both models")
    void shouldCheckChatSlotsHealthForBothModels() {
        // Given - mock successful retry for both slots
        mockSuccessfulRetryForAllSlots();

        // When
        var results = service.checkChatSlotsHealth();

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).containsKeys("nlp-small", "nlp-big");
        assertThat(results.get("nlp-small").isHealthy()).isTrue();
        assertThat(results.get("nlp-big").isHealthy()).isTrue();
    }

    @Test
    @DisplayName("should provide overall system health when all healthy")
    void shouldProvideOverallSystemHealthWhenAllHealthy() {
        // Given - all subsystems healthy
        mockHealthyLlm();
        mockHealthyEmbedding();
        mockHealthyDatabase();
        
        // When
        var result = service.getOverallSystemHealth();

        // Then
        assertThat(result.isOverallHealthy()).isTrue();
        assertThat(result.llmHealth().isHealthy()).isTrue();
        assertThat(result.embeddingHealth().healthy()).isTrue();
        assertThat(result.databaseHealth().healthy()).isTrue();
        assertThat(result.chatSlotsHealth()).hasSize(2);
    }

    @Test
    @DisplayName("should report overall unhealthy when embedding fails")
    void shouldReportOverallUnhealthyWhenEmbeddingFails() {
        // Given - LLM healthy but embedding fails
        mockHealthyLlm();
        mockHealthyDatabase();
        setLastEmbeddingStatusToUnhealthy();

        // When
        var result = service.getOverallSystemHealth();

        // Then
        assertThat(result.isOverallHealthy()).isFalse();
        assertThat(result.llmHealth().isHealthy()).isTrue();
        assertThat(result.embeddingHealth().healthy()).isFalse();
    }

    @Test
    @DisplayName("should report overall unhealthy when database connectivity fails")
    void shouldReportOverallUnhealthyWhenDatabaseFails() {
        mockHealthyLlm();
        mockHealthyEmbedding();
        doThrow(new RuntimeException("neo4j unavailable")).when(neo4jDriver).verifyConnectivity();

        var result = service.getOverallSystemHealth();

        assertThat(result.isOverallHealthy()).isFalse();
        assertThat(result.databaseHealth().healthy()).isFalse();
        assertThat(result.databaseHealth().error()).contains("neo4j unavailable");
    }

    @Test
    @DisplayName("should skip health checks when disabled")
    void shouldSkipHealthChecksWhenDisabled() {
        // Given
        ReflectionTestUtils.setField(service, "healthEnabled", false);

        // When
        var llmResult = service.checkLlmHealth();

        // Then
        assertThat(llmResult.isHealthy()).isTrue();
        assertThat(llmResult.getModelName()).isEqualTo("disabled");
        verifyNoInteractions(retryableHealthChecker);
    }

    @Test
    @DisplayName("should handle startup health check without exceptions")
    void shouldHandleStartupHealthCheckWithoutExceptions() {
        // Given
        mockHealthyLlm();
        mockHealthyEmbedding();
        mockHealthyDatabase();
        // Override global test property to allow startup health check to run in this unit test
        ReflectionTestUtils.setField(service, "startupHealthCheckEnabled", true);

        // When - should not throw exceptions
        service.performStartupHealthCheck();

        // Then - basic verification that method completed
        verify(embeddingModel).embed(anyString());
    }

    // Simplified helper methods that avoid complex generic mocking

    private void mockSuccessfulRetryChain(HealthMetricsCollector.ModelHealthStatus healthStatus) {
        // Mock the success path by mocking the final recordSuccess call
        when(healthMetricsCollector.recordSuccess(anyString(), anyString(), anyLong(), anyLong(), anyInt()))
            .thenReturn(healthStatus);
        
        // Mock successful retry result (simplified - just ensure it doesn't fail)
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> mockRetryResult =
                typedRetryResultMock();
        when(mockRetryResult.isSuccess()).thenReturn(true);
        when(mockRetryResult.getResult()).thenReturn(ModelHealthValidator.HealthCheckResult.success("OK"));
        when(mockRetryResult.getLastAttemptDurationMs()).thenReturn(100L);
        when(mockRetryResult.getTotalDurationMs()).thenReturn(100L);
        when(mockRetryResult.getAttemptsUsed()).thenReturn(1);

        when(retryableHealthChecker.executeWithRetry(
                anyString(),
                any(RetryableHealthChecker.RetryConfig.class),
                any(java.util.function.Supplier.class)
        )).thenReturn(mockRetryResult);
    }

    private void mockFailedRetryChain(HealthMetricsCollector.ModelHealthStatus healthStatus) {
        // Mock the failure path by mocking the final recordFailure call
        when(healthMetricsCollector.recordFailure(anyString(), anyString(), anyLong(), anyLong(), anyInt()))
            .thenReturn(healthStatus);
        
        // Mock failed retry result
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> mockRetryResult =
                typedRetryResultMock();
        when(mockRetryResult.isSuccess()).thenReturn(false);
        when(mockRetryResult.getLastException()).thenReturn(new RuntimeException("Connection failed"));
        when(mockRetryResult.getLastAttemptDurationMs()).thenReturn(100L);
        when(mockRetryResult.getTotalDurationMs()).thenReturn(300L);
        when(mockRetryResult.getAttemptsUsed()).thenReturn(3);

        when(retryableHealthChecker.executeWithRetry(
                anyString(),
                any(RetryableHealthChecker.RetryConfig.class),
                any(java.util.function.Supplier.class)
        )).thenReturn(mockRetryResult);
    }

    private void mockSuccessfulRetryForAllSlots() {
        // Simple mock that works for chat slots health check
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> mockRetryResult =
                typedRetryResultMock();
        when(mockRetryResult.isSuccess()).thenReturn(true);
        when(mockRetryResult.getLastAttemptDurationMs()).thenReturn(100L);
        when(mockRetryResult.getTotalDurationMs()).thenReturn(100L);
        when(mockRetryResult.getAttemptsUsed()).thenReturn(1);

        when(retryableHealthChecker.executeWithRetry(
                anyString(),
                any(RetryableHealthChecker.RetryConfig.class),
                any(java.util.function.Supplier.class)
        )).thenReturn(mockRetryResult);
    }

    @SuppressWarnings("unchecked")
    private RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> typedRetryResultMock() {
        return (RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult>)
                mock(RetryableHealthChecker.RetryResult.class);
    }

    private void mockHealthyLlm() {
        var healthStatus = new HealthMetricsCollector.ModelHealthStatus(
            true, TEST_MODEL_ID, "OK", 100L, 100L, 1);
        mockSuccessfulRetryChain(healthStatus);
    }

    private void mockHealthyEmbedding() {
        float[] testVector = new float[384];
        lenient().when(embeddingModel.embed(anyString())).thenReturn(testVector);
        lenient().when(embeddingModel.dimensions()).thenReturn(384);
    }

    private void mockHealthyDatabase() {
        doNothing().when(neo4jDriver).verifyConnectivity();
    }

    private void setLastEmbeddingStatusToUnhealthy() {
        ReflectionTestUtils.setField(service, "lastEmbeddingStatus", 
            new SystemHealthService.EmbeddingHealthStatus(false, "Connection failed", 100, 0));
    }
}
