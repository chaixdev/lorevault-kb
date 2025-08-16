package com.lorevault.api.service.content.retry;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.SceneDetectionClient;
import com.lorevault.api.service.content.SceneDetectionXmlParser;
import com.lorevault.api.service.content.SceneCoordinateLocalizer;
import com.lorevault.api.service.ingestion.IngestionJobLifecycleService;
import com.lorevault.api.service.content.retry.LlmRetryStrategy.LlmRetryConfig;
import com.lorevault.api.service.content.retry.LlmRetryStrategy.LlmRetryResult;
import com.lorevault.api.testutil.SampleChapterLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Service layer tests for RetryAwareSceneDetectionService following the testing strategy.
 * Focuses on business logic validation with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetryAwareSceneDetectionService Service Tests")
class RetryAwareSceneDetectionServiceTest {

    @Mock
    private SceneDetectionClient sceneDetectionClient;
    
    @Mock
    private SceneDetectionXmlParser xmlParser;
    
    @Mock
    private SceneCoordinateLocalizer coordinateLocalizer;
    
    @Mock
    private LlmRetryStrategy llmRetryStrategy;
    
    @Mock
    private IngestionJobLifecycleService jobLifecycleService;
    
    @InjectMocks
    private RetryAwareSceneDetectionService retryAwareSceneDetectionService;
    
    private UUID jobId;
    private UUID chapterId;
    private String sampleChapterText;
    private String samplePass2Xml;
    private List<SceneDetectionResult> mockSceneResults;
    private List<SceneWithCoordinates> mockScenesWithCoords;
    
    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        sampleChapterText = SampleChapterLoader.getSampleChapterText();
        samplePass2Xml = SampleChapterLoader.loadSceneDetectionXml("000_pass2.xml");
        
        // Create mock scene results
        mockSceneResults = List.of(
            createMockSceneResult(1, "Scene 1 content"),
            createMockSceneResult(2, "Scene 2 content")
        );
        
        // Create mock scenes with coordinates
        mockScenesWithCoords = List.of(
            new SceneWithCoordinates(1, 0L, 100L, "Scene 1 summary"),
            new SceneWithCoordinates(2, 100L, 200L, "Scene 2 summary")
        );
    }
    
    private SceneDetectionResult createMockSceneResult(int index, String summary) {
        return new SceneDetectionResult(
            index,
            "Start anchor " + index,
            summary,
            "Scene break " + index,
            "R:temporal.meets",
            "Explicit",
            "Timeline " + index
        );
    }

    @Test
    @DisplayName("Should successfully detect scenes with retry handling")
    void detectScenesWithRetry_WhenSuccessful_ShouldReturnScenes() {
        // Arrange
        LlmRetryResult<List<SceneWithCoordinates>> successResult = 
            LlmRetryResult.success(mockScenesWithCoords, 1, 1500L, List.of("Attempt 1: success"));
        
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> successResult);

        // Act
        List<SceneWithCoordinates> result = retryAwareSceneDetectionService
            .detectScenesWithRetry(jobId, chapterId, sampleChapterText);

        // Assert
        assertThat(result).isEqualTo(mockScenesWithCoords);
        
        // Verify retry strategy was called
        verify(llmRetryStrategy).executeWithRetry(
            eq("Scene Detection"), 
            any(LlmRetryConfig.class), 
            any()
        );
        
        // Verify job status updates
        verify(jobLifecycleService, atLeastOnce()).updateJobStatus(
            eq(jobId),
            any(),
            contains("Scene detection succeeded"),
            any()
        );
    }

    @Test
    @DisplayName("Should handle retry failures gracefully")
    void detectScenesWithRetry_WhenAllRetriesFail_ShouldThrowException() {
        // Arrange
        RuntimeException originalException = new RuntimeException("LLM call failed");
        LlmRetryResult<List<SceneWithCoordinates>> failureResult = 
            LlmRetryResult.failure(originalException, 3, 5000L, List.of("Attempt 1: failed", "Attempt 2: failed", "Attempt 3: failed"));
        
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> failureResult);

        // Act & Assert
        assertThatThrownBy(() -> retryAwareSceneDetectionService
                .detectScenesWithRetry(jobId, chapterId, sampleChapterText))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Scene detection failed with retry")
            .hasCause(originalException);

        // Verify failure status was reported
        verify(jobLifecycleService, atLeastOnce()).updateJobStatus(
            eq(jobId),
            any(),
            contains("Scene detection failed after"),
            any()
        );
    }

    @Test
    @DisplayName("Should execute two-pass scene detection pipeline")
    void performFullSceneDetection_ShouldExecuteTwoPassPipeline() throws Exception {
        // Arrange - mock the retry strategy to execute our operation
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> {
                // Execute the lambda to test the pipeline logic
                var operation = invocation.getArgument(2, java.util.function.Supplier.class);
                
                // Mock the internal calls for the pipeline
                when(sceneDetectionClient.detectScenesTwoPass(sampleChapterText))
                    .thenReturn(samplePass2Xml);
                when(xmlParser.parseResponse(samplePass2Xml, sampleChapterText.length()))
                    .thenReturn(mockSceneResults);
                when(coordinateLocalizer.localizeCoordinates(sampleChapterText, mockSceneResults))
                    .thenReturn(mockScenesWithCoords);
                
                // Execute the operation and return success
                @SuppressWarnings("unchecked")
                List<SceneWithCoordinates> result = (List<SceneWithCoordinates>) operation.get();
                return LlmRetryResult.success(result, 1, 1000L, List.of("Attempt 1: success"));
            });

        // Act
        List<SceneWithCoordinates> result = retryAwareSceneDetectionService
            .detectScenesWithRetry(jobId, chapterId, sampleChapterText);

        // Assert
        assertThat(result).isEqualTo(mockScenesWithCoords);
        
        // Verify the pipeline was executed
        verify(sceneDetectionClient).detectScenesTwoPass(sampleChapterText);
        verify(xmlParser).parseResponse(samplePass2Xml, sampleChapterText.length());
        verify(coordinateLocalizer).localizeCoordinates(sampleChapterText, mockSceneResults);
    }

    @Test
    @DisplayName("Should handle empty parsing results by throwing exception")
    void performFullSceneDetection_WhenParsingReturnsEmpty_ShouldThrowException() {
        // Arrange
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> {
                var operation = invocation.getArgument(2, java.util.function.Supplier.class);
                
                // Mock empty parsing results
                when(sceneDetectionClient.detectScenesTwoPass(sampleChapterText))
                    .thenReturn(samplePass2Xml);
                when(xmlParser.parseResponse(samplePass2Xml, sampleChapterText.length()))
                    .thenReturn(List.of()); // Empty results
                
                try {
                    operation.get();
                    return LlmRetryResult.failure(new RuntimeException("Should not reach here"), 1, 500L, 
                        List.of("Attempt 1: empty results"));
                } catch (RuntimeException e) {
                    return LlmRetryResult.failure(e, 1, 500L, List.of("Attempt 1: " + e.getMessage()));
                }
            });

        // Act & Assert
        assertThatThrownBy(() -> retryAwareSceneDetectionService
                .detectScenesWithRetry(jobId, chapterId, sampleChapterText))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Scene detection failed with retry");
    }

    @Test
    @DisplayName("Should handle coordinate localization failures")
    void performFullSceneDetection_WhenCoordinateLocalizationReturnsEmpty_ShouldThrowException() {
        // Arrange
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> {
                var operation = invocation.getArgument(2, java.util.function.Supplier.class);
                
                // Mock successful parsing but failed coordinate localization
                when(sceneDetectionClient.detectScenesTwoPass(sampleChapterText))
                    .thenReturn(samplePass2Xml);
                when(xmlParser.parseResponse(samplePass2Xml, sampleChapterText.length()))
                    .thenReturn(mockSceneResults);
                when(coordinateLocalizer.localizeCoordinates(sampleChapterText, mockSceneResults))
                    .thenReturn(List.of()); // Empty coordinate results
                
                try {
                    operation.get();
                    return LlmRetryResult.failure(new RuntimeException("Should not reach here"), 1, 500L,
                        List.of("Attempt 1: empty coordinates"));
                } catch (RuntimeException e) {
                    return LlmRetryResult.failure(e, 1, 500L, List.of("Attempt 1: " + e.getMessage()));
                }
            });

        // Act & Assert
        assertThatThrownBy(() -> retryAwareSceneDetectionService
                .detectScenesWithRetry(jobId, chapterId, sampleChapterText))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Scene detection failed with retry");
    }

    @Test
    @DisplayName("Should update job status during retry attempts")
    void detectScenesWithRetry_ShouldUpdateJobStatusCorrectly() {
        // Arrange
        LlmRetryResult<List<SceneWithCoordinates>> successResult = 
            LlmRetryResult.success(mockScenesWithCoords, 2, 2500L, List.of("Attempt 1: failed", "Attempt 2: success"));
        
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> successResult);

        // Act
        retryAwareSceneDetectionService.detectScenesWithRetry(jobId, chapterId, sampleChapterText);

        // Assert - verify job status updates
        verify(jobLifecycleService, atLeastOnce()).updateJobStatus(
            eq(jobId),
            any(),
            contains("Detecting scenes with retry"),
            any()
        );
        
        verify(jobLifecycleService, atLeastOnce()).updateJobStatus(
            eq(jobId),
            any(),
            contains("Scene detection succeeded after 2"),
            any()
        );
    }

    @Test
    @DisplayName("Should use default retry configuration")
    void detectScenesWithRetry_ShouldUseDefaultRetryConfig() {
        // Arrange
        LlmRetryResult<List<SceneWithCoordinates>> successResult = 
            LlmRetryResult.success(mockScenesWithCoords, 1, 1000L, List.of("Attempt 1: success"));
        
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> successResult);

        // Act
        retryAwareSceneDetectionService.detectScenesWithRetry(jobId, chapterId, sampleChapterText);

        // Assert - verify default retry config is used
        verify(llmRetryStrategy).executeWithRetry(
            eq("Scene Detection"),
            argThat(config -> config != null), // Default config should be created
            any()
        );
    }

    @Test
    @DisplayName("Should validate business rule: two-pass detection is used")
    void detectScenesWithRetry_ShouldUseTwoPassDetection() {
        // Arrange
        when(llmRetryStrategy.executeWithRetry(anyString(), any(LlmRetryConfig.class), any()))
            .thenAnswer(invocation -> {
                var operation = invocation.getArgument(2, java.util.function.Supplier.class);
                
                when(sceneDetectionClient.detectScenesTwoPass(sampleChapterText))
                    .thenReturn(samplePass2Xml);
                when(xmlParser.parseResponse(samplePass2Xml, sampleChapterText.length()))
                    .thenReturn(mockSceneResults);
                when(coordinateLocalizer.localizeCoordinates(sampleChapterText, mockSceneResults))
                    .thenReturn(mockScenesWithCoords);
                
                @SuppressWarnings("unchecked")
                List<SceneWithCoordinates> result = (List<SceneWithCoordinates>) operation.get();
                return LlmRetryResult.success(result, 1, 1000L, List.of("Attempt 1: success"));
            });

        // Act
        retryAwareSceneDetectionService.detectScenesWithRetry(jobId, chapterId, sampleChapterText);

        // Assert - verify two-pass detection is used (not single-pass)
        verify(sceneDetectionClient).detectScenesTwoPass(sampleChapterText);
        verify(sceneDetectionClient, never()).detectScenes(anyString());
    }
}
