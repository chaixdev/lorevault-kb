package com.lorevault.api.service.system;

import com.lorevault.api.application.port.EmbeddingPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("EmbeddingHealthCheckService")
class EmbeddingHealthCheckServiceTest {

    @Mock
    private EmbeddingPort embeddingPort;

    @InjectMocks
    private EmbeddingHealthCheckService service;

    @BeforeEach
    void setUp() {
        // Set private fields using reflection
        ReflectionTestUtils.setField(service, "healthEnabled", true);
        ReflectionTestUtils.setField(service, "testText", "health_check");
        ReflectionTestUtils.setField(service, "configuredExpectedDim", null);
    }

    @Test
    @DisplayName("should return healthy when embedding returns correct dimension")
    void shouldReturnHealthyWhenEmbeddingIsCorrect() {
        double[] vec = new double[384];
    when(embeddingPort.embed("health_check")).thenReturn(vec);
    when(embeddingPort.getDimension()).thenReturn(384);

        var status = service.checkEmbeddingService();
        assertThat(status.healthy()).isTrue();
        assertThat(status.error()).isNull();
        assertThat(status.dimension()).isEqualTo(384);
    }

    @Test
    @DisplayName("should return unhealthy if embedding returns empty vector")
    void shouldReturnUnhealthyOnEmptyVector() {
        when(embeddingPort.embed("health_check")).thenReturn(new double[0]);
        when(embeddingPort.getDimension()).thenReturn(384);
        var status = service.checkEmbeddingService();
        assertThat(status.healthy()).isFalse();
        assertThat(status.error()).contains("Empty vector");
    }

    @Test
    @DisplayName("should return unhealthy if dimension mismatch")
    void shouldReturnUnhealthyOnDimensionMismatch() {
        double[] vec = new double[256];
        when(embeddingPort.embed("health_check")).thenReturn(vec);
        when(embeddingPort.getDimension()).thenReturn(384);
        var status = service.checkEmbeddingService();
        assertThat(status.healthy()).isFalse();
        assertThat(status.error()).contains("Dimension mismatch");
    }

    @Test
    @DisplayName("should use configured expected dimension if set")
    void shouldUseConfiguredExpectedDim() {
        double[] vec = new double[128];
    ReflectionTestUtils.setField(service, "configuredExpectedDim", 128);
        when(embeddingPort.embed("health_check")).thenReturn(vec);
        var status = service.checkEmbeddingService();
        assertThat(status.healthy()).isTrue();
        assertThat(status.dimension()).isEqualTo(128);
    }

    @Test
    @DisplayName("should return unhealthy if embedding throws exception")
    void shouldReturnUnhealthyOnException() {
        when(embeddingPort.embed("health_check")).thenThrow(new RuntimeException("Connection refused"));
        var status = service.checkEmbeddingService();
        assertThat(status.healthy()).isFalse();
        assertThat(status.error()).contains("Connection refused");
    }

    @Test
    @DisplayName("should return healthy if health check is disabled")
    void shouldReturnHealthyIfDisabled() {
    ReflectionTestUtils.setField(service, "healthEnabled", false);
        var status = service.checkEmbeddingService();
        assertThat(status.healthy()).isTrue();
        assertThat(status.error()).isEqualTo("DISABLED");
    }
}
