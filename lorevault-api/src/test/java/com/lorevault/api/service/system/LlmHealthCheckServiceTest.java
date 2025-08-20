package com.lorevault.api.service.system;

import com.lorevault.api.service.system.metrics.HealthMetricsCollector;
import com.lorevault.api.service.system.retry.RetryableHealthChecker;
import com.lorevault.api.service.system.validator.ModelHealthValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Tag;
import java.util.function.Supplier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("LlmHealthCheckService")
class LlmHealthCheckServiceTest {

    @Mock
    private RetryableHealthChecker retryableHealthChecker;

    @Mock
    private ModelHealthValidator modelHealthValidator;

    @Mock
    private HealthMetricsCollector healthMetricsCollector;

    @Mock
    private ApplicationReadyEvent applicationReadyEvent;

    @InjectMocks
    private LlmHealthCheckService service;

    private static final String TEST_MODEL_ID = "gemini-2.5-flash-lite";

    @BeforeEach
    void setUp() {
        // Set up the injected values using ReflectionTestUtils
        ReflectionTestUtils.setField(service, "modelId", TEST_MODEL_ID);
        ReflectionTestUtils.setField(service, "healthEnabled", true);
    }

    @Test
    @DisplayName("should perform startup health check when enabled")
    void shouldPerformStartupHealthCheckWhenEnabled() throws Exception {
        // Given
        ModelHealthValidator.HealthCheckResult validationResult = 
            ModelHealthValidator.HealthCheckResult.success("OK");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> successRetryResult = 
            RetryableHealthChecker.RetryResult.success(validationResult, 1, 100L, 100L);
        HealthMetricsCollector.ModelHealthStatus healthyStatus = 
            new HealthMetricsCollector.ModelHealthStatus(true, TEST_MODEL_ID, "OK", 100L, 100L, 1);

    when(retryableHealthChecker.executeWithRetry(
        eq("health-check-" + TEST_MODEL_ID),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(successRetryResult);
        when(healthMetricsCollector.recordSuccess(TEST_MODEL_ID, "OK", 100L, 100L, 1))
            .thenReturn(healthyStatus);

        // When
        service.performStartupHealthCheck();

        // Then
        verify(retryableHealthChecker).executeWithRetry(
            eq("health-check-" + TEST_MODEL_ID), 
            any(RetryableHealthChecker.RetryConfig.class), 
            any()
        );
        verify(healthMetricsCollector).recordSuccess(TEST_MODEL_ID, "OK", 100L, 100L, 1);
    }

    @Test
    @DisplayName("should skip startup health check when disabled")
    void shouldSkipStartupHealthCheckWhenDisabled() throws Exception {
        // Given
        ReflectionTestUtils.setField(service, "healthEnabled", false);

        // When
        service.performStartupHealthCheck();

        // Then
        verify(retryableHealthChecker, never()).executeWithRetry(any(), any(), any());
        verify(healthMetricsCollector, never()).recordSuccess(any(), any(), anyLong(), anyLong(), anyInt());
        verify(healthMetricsCollector, never()).recordFailure(any(), any(), anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("should handle startup health check failure")
    void shouldHandleStartupHealthCheckFailure() throws Exception {
        // Given
        RuntimeException testException = new RuntimeException("Connection timeout");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> failureRetryResult = 
            RetryableHealthChecker.RetryResult.failure(testException, 3, 500L, 2000L);
        HealthMetricsCollector.ModelHealthStatus unhealthyStatus = 
            new HealthMetricsCollector.ModelHealthStatus(false, TEST_MODEL_ID, "Connection timeout", 500L, 2000L, 3);

    when(retryableHealthChecker.executeWithRetry(
        eq("health-check-" + TEST_MODEL_ID),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(failureRetryResult);
        when(healthMetricsCollector.recordFailure(TEST_MODEL_ID, "Connection timeout", 500L, 2000L, 3))
            .thenReturn(unhealthyStatus);

        // When
        service.performStartupHealthCheck();

        // Then
        verify(healthMetricsCollector).recordFailure(TEST_MODEL_ID, "Connection timeout", 500L, 2000L, 3);
    }

    @Test
    @DisplayName("should check current model health")
    void shouldCheckCurrentModelHealth() throws Exception {
        // Given
        ModelHealthValidator.HealthCheckResult validationResult = 
            ModelHealthValidator.HealthCheckResult.success("OK");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> successRetryResult = 
            RetryableHealthChecker.RetryResult.success(validationResult, 1, 150L, 150L);
        HealthMetricsCollector.ModelHealthStatus expectedStatus = 
            new HealthMetricsCollector.ModelHealthStatus(true, TEST_MODEL_ID, "OK", 150L, 150L, 1);

    when(retryableHealthChecker.executeWithRetry(
        anyString(),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(successRetryResult);
        when(healthMetricsCollector.recordSuccess(TEST_MODEL_ID, "OK", 150L, 150L, 1))
            .thenReturn(expectedStatus);

        // When
        HealthMetricsCollector.ModelHealthStatus result = service.checkCurrentModel();

        // Then
        assertThat(result).isEqualTo(expectedStatus);
        assertThat(result.isHealthy()).isTrue();
        assertThat(result.getModelName()).isEqualTo(TEST_MODEL_ID);
        assertThat(result.getSuccessMessage()).isEqualTo("OK");
    }

    @Test
    @DisplayName("should check all models and return map with current model")
    void shouldCheckAllModelsAndReturnMapWithCurrentModel() throws Exception {
        // Given
        ModelHealthValidator.HealthCheckResult validationResult = 
            ModelHealthValidator.HealthCheckResult.success("OK");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> successRetryResult = 
            RetryableHealthChecker.RetryResult.success(validationResult, 1, 120L, 120L);
        HealthMetricsCollector.ModelHealthStatus expectedStatus = 
            new HealthMetricsCollector.ModelHealthStatus(true, TEST_MODEL_ID, "OK", 120L, 120L, 1);

    when(retryableHealthChecker.executeWithRetry(
        anyString(),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(successRetryResult);
        when(healthMetricsCollector.recordSuccess(TEST_MODEL_ID, "OK", 120L, 120L, 1))
            .thenReturn(expectedStatus);

        // When
        Map<String, HealthMetricsCollector.ModelHealthStatus> result = service.checkAllModels();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result).containsKey(TEST_MODEL_ID);
        assertThat(result.get(TEST_MODEL_ID)).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("should check single model health with retry on failure")
    void shouldCheckSingleModelHealthWithRetryOnFailure() throws Exception {
        // Given
        String customModelId = "custom-model";
        RuntimeException testException = new RuntimeException("Service temporarily unavailable");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> failureRetryResult = 
            RetryableHealthChecker.RetryResult.failure(testException, 2, 300L, 1500L);
        HealthMetricsCollector.ModelHealthStatus expectedStatus = 
            new HealthMetricsCollector.ModelHealthStatus(false, customModelId, "Service temporarily unavailable", 300L, 1500L, 2);

    when(retryableHealthChecker.executeWithRetry(
        eq("health-check-" + customModelId),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(failureRetryResult);
        when(healthMetricsCollector.recordFailure(customModelId, "Service temporarily unavailable", 300L, 1500L, 2))
            .thenReturn(expectedStatus);

        // When
        HealthMetricsCollector.ModelHealthStatus result = service.checkSingleModel(customModelId);

        // Then
        assertThat(result).isEqualTo(expectedStatus);
        assertThat(result.isHealthy()).isFalse();
        assertThat(result.getModelName()).isEqualTo(customModelId);
        assertThat(result.getErrorMessage()).isEqualTo("Service temporarily unavailable");
        assertThat(result.getAttemptsUsed()).isEqualTo(2);
    }

    @Test
    @DisplayName("should handle exception without message in retry result")
    void shouldHandleExceptionWithoutMessageInRetryResult() throws Exception {
        // Given
        RuntimeException testException = new RuntimeException(); // No message
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> failureRetryResult = 
            RetryableHealthChecker.RetryResult.failure(testException, 1, 200L, 200L);
        HealthMetricsCollector.ModelHealthStatus expectedStatus = 
            new HealthMetricsCollector.ModelHealthStatus(false, TEST_MODEL_ID, "Unknown error", 200L, 200L, 1);

    when(retryableHealthChecker.executeWithRetry(
        anyString(),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(failureRetryResult);
    when(healthMetricsCollector.recordFailure(eq(TEST_MODEL_ID), isNull(), eq(200L), eq(200L), eq(1)))
            .thenReturn(expectedStatus);

        // When
        HealthMetricsCollector.ModelHealthStatus result = service.checkSingleModel(TEST_MODEL_ID);

        // Then
        assertThat(result.getErrorMessage()).isEqualTo("Unknown error");
    }

    @Test
    @DisplayName("should handle null exception in retry result")
    void shouldHandleNullExceptionInRetryResult() throws Exception {
        // Given
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> failureRetryResult = 
            RetryableHealthChecker.RetryResult.failure(null, 1, 200L, 200L);
        HealthMetricsCollector.ModelHealthStatus expectedStatus = 
            new HealthMetricsCollector.ModelHealthStatus(false, TEST_MODEL_ID, "Unknown error", 200L, 200L, 1);

    when(retryableHealthChecker.executeWithRetry(
        anyString(),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(failureRetryResult);
        when(healthMetricsCollector.recordFailure(TEST_MODEL_ID, "Unknown error", 200L, 200L, 1))
            .thenReturn(expectedStatus);

        // When
        HealthMetricsCollector.ModelHealthStatus result = service.checkSingleModel(TEST_MODEL_ID);

        // Then
        assertThat(result.getErrorMessage()).isEqualTo("Unknown error");
    }

    @Test
    @DisplayName("should return healthy when service is disabled")
    void shouldReturnHealthyWhenServiceIsDisabled() {
        // Given
        ReflectionTestUtils.setField(service, "healthEnabled", false);

        // When
        boolean result = service.isLlmServiceHealthy();

        // Then
        assertThat(result).isTrue();
        verify(retryableHealthChecker, never()).executeWithRetry(any(), any(), any());
    }

    @Test
    @DisplayName("should return healthy when health check passes")
    void shouldReturnHealthyWhenHealthCheckPasses() throws Exception {
        // Given
        ModelHealthValidator.HealthCheckResult validationResult = 
            ModelHealthValidator.HealthCheckResult.success("OK");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> successRetryResult = 
            RetryableHealthChecker.RetryResult.success(validationResult, 1, 80L, 80L);
        HealthMetricsCollector.ModelHealthStatus healthyStatus = 
            new HealthMetricsCollector.ModelHealthStatus(true, TEST_MODEL_ID, "OK", 80L, 80L, 1);

    when(retryableHealthChecker.executeWithRetry(
        anyString(),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(successRetryResult);
        when(healthMetricsCollector.recordSuccess(TEST_MODEL_ID, "OK", 80L, 80L, 1))
            .thenReturn(healthyStatus);

        // When
        boolean result = service.isLlmServiceHealthy();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return unhealthy when health check fails")
    void shouldReturnUnhealthyWhenHealthCheckFails() throws Exception {
        // Given
        RuntimeException testException = new RuntimeException("Model unavailable");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> failureRetryResult = 
            RetryableHealthChecker.RetryResult.failure(testException, 3, 400L, 2500L);
        HealthMetricsCollector.ModelHealthStatus unhealthyStatus = 
            new HealthMetricsCollector.ModelHealthStatus(false, TEST_MODEL_ID, "Model unavailable", 400L, 2500L, 3);

    when(retryableHealthChecker.executeWithRetry(
        anyString(),
        any(RetryableHealthChecker.RetryConfig.class),
        ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
    )).thenReturn(failureRetryResult);
        when(healthMetricsCollector.recordFailure(TEST_MODEL_ID, "Model unavailable", 400L, 2500L, 3))
            .thenReturn(unhealthyStatus);

        // When
        boolean result = service.isLlmServiceHealthy();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should get current model metrics")
    void shouldGetCurrentModelMetrics() throws Exception {
        // Given
        HealthMetricsCollector.ModelMetrics expectedMetrics = mock(HealthMetricsCollector.ModelMetrics.class);
        when(healthMetricsCollector.getModelMetrics(TEST_MODEL_ID)).thenReturn(expectedMetrics);

        // When
        HealthMetricsCollector.ModelMetrics result = service.getCurrentModelMetrics();

        // Then
        assertThat(result).isEqualTo(expectedMetrics);
        verify(healthMetricsCollector).getModelMetrics(TEST_MODEL_ID);
    }

    @Test
    @DisplayName("should get last health status")
    void shouldGetLastHealthStatus() throws Exception {
        // Given
        HealthMetricsCollector.ModelHealthStatus expectedStatus = 
            new HealthMetricsCollector.ModelHealthStatus(true, TEST_MODEL_ID, "OK", 90L, 90L, 1);
        when(healthMetricsCollector.getLastStatus(TEST_MODEL_ID)).thenReturn(expectedStatus);

        // When
        HealthMetricsCollector.ModelHealthStatus result = service.getLastHealthStatus();

        // Then
        assertThat(result).isEqualTo(expectedStatus);
        verify(healthMetricsCollector).getLastStatus(TEST_MODEL_ID);
    }

    @Test
    @DisplayName("should use correct retry configuration")
    void shouldUseCorrectRetryConfiguration() throws Exception {
        // Given
        ModelHealthValidator.HealthCheckResult validationResult =
            ModelHealthValidator.HealthCheckResult.success("OK");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> successRetryResult =
            RetryableHealthChecker.RetryResult.success(validationResult, 1, 100L, 100L);

        when(retryableHealthChecker.executeWithRetry(
            anyString(), any(RetryableHealthChecker.RetryConfig.class), ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
        )).thenReturn(successRetryResult);

        // When
        service.checkCurrentModel();

        // Then - verify that executeWithRetry is called with the correct operation name and a retry config
        verify(retryableHealthChecker).executeWithRetry(
            eq("health-check-" + TEST_MODEL_ID), 
            any(RetryableHealthChecker.RetryConfig.class), 
            ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()
        );
    }

    @Test
    @DisplayName("should call validator connectivity test in retry operation")
    void shouldCallValidatorConnectivityTestInRetryOperation() throws Exception {
        // Given
        ModelHealthValidator.HealthCheckResult validationResult = 
            ModelHealthValidator.HealthCheckResult.success("OK");
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> successRetryResult = 
            RetryableHealthChecker.RetryResult.success(validationResult, 1, 60L, 60L);

        doReturn(validationResult)
            .when(modelHealthValidator)
            .performConnectivityTest(TEST_MODEL_ID);
    when(retryableHealthChecker.executeWithRetry(anyString(), any(RetryableHealthChecker.RetryConfig.class), ArgumentMatchers.<Supplier<ModelHealthValidator.HealthCheckResult>>any()))
            .thenAnswer(invocation -> {
                // Execute the supplier to verify it calls the validator
                @SuppressWarnings("unchecked")
                var supplier = (java.util.function.Supplier<ModelHealthValidator.HealthCheckResult>) invocation.getArgument(2);
                supplier.get();
                return successRetryResult;
            });

        // When
        service.checkSingleModel(TEST_MODEL_ID);

        // Then
        verify(modelHealthValidator).performConnectivityTest(TEST_MODEL_ID);
    }
}