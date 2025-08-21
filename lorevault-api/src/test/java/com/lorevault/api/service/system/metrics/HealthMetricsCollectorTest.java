package com.lorevault.api.service.system.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Tag("unit")
@DisplayName("HealthMetricsCollector")
class HealthMetricsCollectorTest {

    private HealthMetricsCollector collector;
    private static final String TEST_MODEL = "test-model";

    @BeforeEach
    void setUp() {
        collector = new HealthMetricsCollector();
    }

    @Test
    @DisplayName("should record successful health check")
    void shouldRecordSuccessfulHealthCheck() {
        // When
        var status = collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);

        // Then
        assertThat(status.isHealthy()).isTrue();
        assertThat(status.getModelName()).isEqualTo(TEST_MODEL);
        assertThat(status.getSuccessMessage()).isEqualTo("OK");
        assertThat(status.getErrorMessage()).isNull();
        assertThat(status.getLastAttemptDurationMs()).isEqualTo(50L);
        assertThat(status.getTotalDurationMs()).isEqualTo(100L);
        assertThat(status.getAttemptsUsed()).isEqualTo(1);
        assertThat(status.getTimestamp()).isCloseTo(Instant.now(), within(java.time.Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("should record failed health check")
    void shouldRecordFailedHealthCheck() {
        // When
        var status = collector.recordFailure(TEST_MODEL, "Connection timeout", 200L, 500L, 3);

        // Then
        assertThat(status.isHealthy()).isFalse();
        assertThat(status.getModelName()).isEqualTo(TEST_MODEL);
        assertThat(status.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(status.getSuccessMessage()).isNull();
        assertThat(status.getLastAttemptDurationMs()).isEqualTo(200L);
        assertThat(status.getTotalDurationMs()).isEqualTo(500L);
        assertThat(status.getAttemptsUsed()).isEqualTo(3);
    }

    @Test
    @DisplayName("should track metrics for successful checks")
    void shouldTrackMetricsForSuccessfulChecks() {
        // Given
        collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);
        collector.recordSuccess(TEST_MODEL, "OK", 75L, 150L, 2);

        // When
        var metrics = collector.getModelMetrics(TEST_MODEL);

        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalChecks()).isEqualTo(2);
        assertThat(metrics.getSuccessfulChecks()).isEqualTo(2);
        assertThat(metrics.getFailedChecks()).isEqualTo(0);
        assertThat(metrics.getTotalResponseTimeMs()).isEqualTo(250L); // 100 + 150
        assertThat(metrics.getTotalAttempts()).isEqualTo(3); // 1 + 2
        assertThat(metrics.getSuccessRate()).isEqualTo(1.0);
        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(125.0); // 250 / 2
        assertThat(metrics.getAverageAttemptsPerCheck()).isEqualTo(1.5); // 3 / 2
    }

    @Test
    @DisplayName("should track metrics for failed checks")
    void shouldTrackMetricsForFailedChecks() {
        // Given
        collector.recordFailure(TEST_MODEL, "Error 1", 100L, 300L, 2);
        collector.recordFailure(TEST_MODEL, "Error 2", 150L, 400L, 3);

        // When
        var metrics = collector.getModelMetrics(TEST_MODEL);

        // Then
        assertThat(metrics.getTotalChecks()).isEqualTo(2);
        assertThat(metrics.getSuccessfulChecks()).isEqualTo(0);
        assertThat(metrics.getFailedChecks()).isEqualTo(2);
        assertThat(metrics.getSuccessRate()).isEqualTo(0.0);
        assertThat(metrics.getTotalResponseTimeMs()).isEqualTo(700L); // 300 + 400
        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(350.0); // 700 / 2
    }

    @Test
    @DisplayName("should track metrics for mixed success and failure")
    void shouldTrackMetricsForMixedResults() {
        // Given
        collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);
        collector.recordFailure(TEST_MODEL, "Error", 100L, 200L, 2);
        collector.recordSuccess(TEST_MODEL, "OK", 75L, 150L, 1);

        // When
        var metrics = collector.getModelMetrics(TEST_MODEL);

        // Then
        assertThat(metrics.getTotalChecks()).isEqualTo(3);
        assertThat(metrics.getSuccessfulChecks()).isEqualTo(2);
        assertThat(metrics.getFailedChecks()).isEqualTo(1);
        assertThat(metrics.getSuccessRate()).isCloseTo(0.6667, within(0.001));
        assertThat(metrics.getTotalResponseTimeMs()).isEqualTo(450L); // 100 + 200 + 150
        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(150.0); // 450 / 3
    }

    @Test
    @DisplayName("should return last recorded status")
    void shouldReturnLastRecordedStatus() {
        // Given
        collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);
        var lastStatus = collector.recordFailure(TEST_MODEL, "Recent error", 100L, 200L, 2);

        // When
        var retrievedStatus = collector.getLastStatus(TEST_MODEL);

        // Then
        assertThat(retrievedStatus).isEqualTo(lastStatus);
        assertThat(retrievedStatus.isHealthy()).isFalse();
        assertThat(retrievedStatus.getErrorMessage()).isEqualTo("Recent error");
    }

    @Test
    @DisplayName("should track first and last check times")
    void shouldTrackFirstAndLastCheckTimes() {
        // Given
        Instant beforeFirst = Instant.now();
        collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);
        Instant afterFirst = Instant.now();
        
        // Small delay to ensure different timestamps
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        Instant beforeSecond = Instant.now();
        collector.recordFailure(TEST_MODEL, "Error", 100L, 200L, 2);
        Instant afterSecond = Instant.now();

        // When
        var metrics = collector.getModelMetrics(TEST_MODEL);

        // Then
        assertThat(metrics.getFirstCheckTime())
            .isAfter(beforeFirst)
            .isBefore(afterFirst);
        assertThat(metrics.getLastCheckTime())
            .isAfter(beforeSecond)
            .isBefore(afterSecond);
        assertThat(metrics.getLastCheckTime())
            .isAfter(metrics.getFirstCheckTime());
    }

    @Test
    @DisplayName("should return null for unknown model metrics")
    void shouldReturnNullForUnknownModel() {
        // When/Then
        assertThat(collector.getModelMetrics("unknown-model")).isNull();
        assertThat(collector.getLastStatus("unknown-model")).isNull();
    }

    @Test
    @DisplayName("should check if model is currently healthy")
    void shouldCheckIfModelIsCurrentlyHealthy() {
        // Given - no checks yet
        assertThat(collector.isModelCurrentlyHealthy(TEST_MODEL)).isFalse();

        // When - successful check
        collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);
        // Then
        assertThat(collector.isModelCurrentlyHealthy(TEST_MODEL)).isTrue();

        // When - failed check
        collector.recordFailure(TEST_MODEL, "Error", 100L, 200L, 2);
        // Then
        assertThat(collector.isModelCurrentlyHealthy(TEST_MODEL)).isFalse();
    }

    @Test
    @DisplayName("should reset metrics for specific model")
    void shouldResetMetricsForSpecificModel() {
        // Given
        collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);
        collector.recordSuccess("other-model", "OK", 60L, 120L, 1);
        
        assertThat(collector.getModelMetrics(TEST_MODEL)).isNotNull();
        assertThat(collector.getModelMetrics("other-model")).isNotNull();

        // When
        collector.resetModelMetrics(TEST_MODEL);

        // Then
        assertThat(collector.getModelMetrics(TEST_MODEL)).isNull();
        assertThat(collector.getModelMetrics("other-model")).isNotNull(); // other model unaffected
    }

    @Test
    @DisplayName("should reset all metrics")
    void shouldResetAllMetrics() {
        // Given
        collector.recordSuccess(TEST_MODEL, "OK", 50L, 100L, 1);
        collector.recordSuccess("other-model", "OK", 60L, 120L, 1);
        
        assertThat(collector.getModelMetrics(TEST_MODEL)).isNotNull();
        assertThat(collector.getModelMetrics("other-model")).isNotNull();

        // When
        collector.resetAllMetrics();

        // Then
        assertThat(collector.getModelMetrics(TEST_MODEL)).isNull();
        assertThat(collector.getModelMetrics("other-model")).isNull();
    }

    @Test
    @DisplayName("should handle zero division in metrics calculations")
    void shouldHandleZeroDivisionInMetricsCalculations() {
        // Given - create a new ModelMetrics instance directly to test edge case
        var metrics = new HealthMetricsCollector.ModelMetrics();

        // When/Then - should not throw division by zero with no recorded checks
        assertThat(metrics.getSuccessRate()).isEqualTo(0.0);
        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(0.0);
        assertThat(metrics.getAverageAttemptsPerCheck()).isEqualTo(0.0);
    }
}