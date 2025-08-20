package com.lorevault.api.service.shared;

import com.lorevault.api.configuration.properties.LoreVaultPromptProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PromptLoaderService.
 * Tests resource loading, caching, error handling, and prompt template functionality.
 */
@Tag("unit")
@DisplayName("PromptLoaderService")
class PromptLoaderServiceTest {

    @Mock
    private LoreVaultPromptProperties promptProperties;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource mockResource;

    private PromptLoaderService promptLoaderService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        promptLoaderService = new PromptLoaderService(promptProperties, resourceLoader);
    }

    @Test
    @DisplayName("should initialize and pre-load critical prompts successfully")
    void shouldInitializeAndPreLoadCriticalPromptsSuccessfully() throws IOException {
        // Given
        when(promptProperties.basePath()).thenReturn("classpath:prompts");
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("classpath:prompts/scene-detection-pass1.txt");
        when(promptProperties.getSceneDetectionPass2Path()).thenReturn("classpath:prompts/scene-detection-pass2.txt");
        when(promptProperties.getRagAnswerGenerationPath()).thenReturn("classpath:prompts/rag-answer-generation.txt");

        setupMockResource("Test scene detection pass 1 prompt");

        // When
        promptLoaderService.initialize();

        // Then - Should have attempted to load the prompts
        verify(resourceLoader, atLeastOnce()).getResource(anyString());
    }

    @Test
    @DisplayName("should cache and return scene detection pass 1 prompt template")
    void shouldCacheAndReturnSceneDetectionPass1PromptTemplate() throws IOException {
        // Given
        String promptPath = "classpath:prompts/scene-detection-pass1.txt";
        String promptContent = "You are a scene detection AI. Task: {{task}}";
        
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn(promptPath);
        setupMockResource(promptContent);

        // When - First call
        PromptTemplate template1 = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        
        // When - Second call (should use cache)
        PromptTemplate template2 = promptLoaderService.getSceneDetectionPass1PromptTemplate();

        // Then
        assertThat(template1).isNotNull();
        assertThat(template2).isSameAs(template1); // Same instance due to caching
        verify(resourceLoader, times(1)).getResource(promptPath); // Only called once due to caching
    }

    @Test
    @DisplayName("should cache and return scene detection pass 2 prompt template")
    void shouldCacheAndReturnSceneDetectionPass2PromptTemplate() throws IOException {
        // Given
        String promptPath = "classpath:prompts/scene-detection-pass2.txt";
        String promptContent = "Normalize the scene detection results. Context: {{context}}";
        
        when(promptProperties.getSceneDetectionPass2Path()).thenReturn(promptPath);
        setupMockResource(promptContent);

        // When
        PromptTemplate template = promptLoaderService.getSceneDetectionPass2PromptTemplate();

        // Then
        assertThat(template).isNotNull();
        verify(resourceLoader).getResource(promptPath);
    }

    @Test
    @DisplayName("should return pass 2 template for legacy scene detection method")
    void shouldReturnPass2TemplateForLegacySceneDetectionMethod() throws IOException {
        // Given
        String promptPath = "classpath:prompts/scene-detection-pass2.txt";
        String promptContent = "Legacy scene detection prompt";
        
        when(promptProperties.getSceneDetectionPass2Path()).thenReturn(promptPath);
        setupMockResource(promptContent);

        // When
        PromptTemplate legacyTemplate = promptLoaderService.getSceneDetectionPromptTemplate();
        PromptTemplate pass2Template = promptLoaderService.getSceneDetectionPass2PromptTemplate();

        // Then - Should be the same instance
        assertThat(legacyTemplate).isSameAs(pass2Template);
    }

    @Test
    @DisplayName("should cache and return RAG answer generation prompt template")
    void shouldCacheAndReturnRagAnswerGenerationPromptTemplate() throws IOException {
        // Given
        String promptPath = "classpath:prompts/rag-answer-generation.txt";
        String promptContent = "Generate an answer based on the context: {{context}} for question: {{question}}";
        
        when(promptProperties.getRagAnswerGenerationPath()).thenReturn(promptPath);
        setupMockResource(promptContent);

        // When
        PromptTemplate template = promptLoaderService.getRagAnswerGenerationPromptTemplate();

        // Then
        assertThat(template).isNotNull();
        verify(resourceLoader).getResource(promptPath);
    }

    @Test
    @DisplayName("should render template with custom delimiters")
    void shouldRenderTemplateWithCustomDelimiters() throws IOException {
        // Given
        String promptContent = "Hello {{name}}, your task is {{task}}.";
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("test.txt");
        setupMockResource(promptContent);

        // When
        PromptTemplate template = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        String rendered = template.render(Map.of("name", "AI", "task", "scene detection"));

        // Then
        assertThat(rendered).isEqualTo("Hello AI, your task is scene detection.");
    }

    @Test
    @DisplayName("should handle missing variables in template rendering")
    void shouldHandleMissingVariablesInTemplateRendering() throws IOException {
        // Given
        String promptContent = "Hello {{name}}, your task is {{task}}.";
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("test.txt");
        setupMockResource(promptContent);

        // When
        PromptTemplate template = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        String rendered = template.render(Map.of("name", "AI")); // missing 'task'

        // Then
        assertThat(rendered).isEqualTo("Hello AI, your task is {{task}}."); // Missing variable left unchanged
    }

    @Test
    @DisplayName("should handle null values in template rendering")
    void shouldHandleNullValuesInTemplateRendering() throws IOException {
        // Given
        String promptContent = "Hello {{name}}, status: {{status}}.";
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("test.txt");
        setupMockResource(promptContent);

        // When
        PromptTemplate template = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "AI");
        variables.put("status", null);
        String rendered = template.render(variables);

        // Then
        assertThat(rendered).isEqualTo("Hello AI, status: ."); // Null replaced with empty string
    }

    @Test
    @DisplayName("should throw runtime exception when resource not found")
    void shouldThrowRuntimeExceptionWhenResourceNotFound() throws IOException {
        // Given
        String promptPath = "classpath:prompts/nonexistent.txt";
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn(promptPath);
        when(resourceLoader.getResource(promptPath)).thenReturn(mockResource);
        when(mockResource.exists()).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> promptLoaderService.getSceneDetectionPass1PromptTemplate())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Prompt resource not found: " + promptPath);
    }

    @Test
    @DisplayName("should throw runtime exception when resource is empty")
    void shouldThrowRuntimeExceptionWhenResourceIsEmpty() throws IOException {
        // Given
        String promptPath = "classpath:prompts/empty.txt";
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn(promptPath);
        setupMockResource("   "); // Only whitespace

        // When & Then
        assertThatThrownBy(() -> promptLoaderService.getSceneDetectionPass1PromptTemplate())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Prompt resource is empty: " + promptPath);
    }

    @Test
    @DisplayName("should throw runtime exception when IO error occurs")
    void shouldThrowRuntimeExceptionWhenIOErrorOccurs() throws IOException {
        // Given
        String promptPath = "classpath:prompts/failing.txt";
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn(promptPath);
        when(resourceLoader.getResource(promptPath)).thenReturn(mockResource);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getContentAsString(any())).thenThrow(new IOException("Read failed"));

        // When & Then
        assertThatThrownBy(() -> promptLoaderService.getSceneDetectionPass1PromptTemplate())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to load prompt template from resource: " + promptPath)
            .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("should clear cache and reset cache size")
    void shouldClearCacheAndResetCacheSize() throws IOException {
        // Given - Load a template to populate cache
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("test.txt");
        setupMockResource("Test prompt");
        promptLoaderService.getSceneDetectionPass1PromptTemplate();
        
        assertThat(promptLoaderService.getCacheSize()).isGreaterThan(0);

        // When
        promptLoaderService.clearCache();

        // Then
        assertThat(promptLoaderService.getCacheSize()).isZero();
    }

    @Test
    @DisplayName("should track cache size correctly")
    void shouldTrackCacheSizeCorrectly() throws IOException {
        // Given
        assertThat(promptLoaderService.getCacheSize()).isZero();

        // When - Load different templates
        setupMultiplePrompts();
        promptLoaderService.getSceneDetectionPass1PromptTemplate();
        int sizeAfterFirst = promptLoaderService.getCacheSize();
        
        promptLoaderService.getSceneDetectionPass2PromptTemplate();
        int sizeAfterSecond = promptLoaderService.getCacheSize();
        
        promptLoaderService.getRagAnswerGenerationPromptTemplate();
        int sizeAfterThird = promptLoaderService.getCacheSize();

        // Then
        assertThat(sizeAfterFirst).isEqualTo(1);
        assertThat(sizeAfterSecond).isEqualTo(2);
        assertThat(sizeAfterThird).isEqualTo(3);
    }

    @Test
    @DisplayName("should not reload cached templates from resource")
    void shouldNotReloadCachedTemplatesFromResource() throws IOException {
        // Given
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("test.txt");
        setupMockResource("Cached prompt");

        // When - Load same template multiple times
        PromptTemplate template1 = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        PromptTemplate template2 = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        PromptTemplate template3 = promptLoaderService.getSceneDetectionPass1PromptTemplate();

        // Then - Resource should only be loaded once
        verify(resourceLoader, times(1)).getResource("test.txt");
        assertThat(template1).isSameAs(template2).isSameAs(template3);
    }

    @Test
    @DisplayName("should handle concurrent access to cache")
    void shouldHandleConcurrentAccessToCache() throws IOException {
        // Given
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("test.txt");
        setupMockResource("Concurrent test prompt");

        // When - Multiple threads access the same template
        PromptTemplate[] templates = new PromptTemplate[10];
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                templates[index] = promptLoaderService.getSceneDetectionPass1PromptTemplate();
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Then - All templates should be the same instance (cached)
        for (int i = 1; i < templates.length; i++) {
            assertThat(templates[i]).isSameAs(templates[0]);
        }
        
        // Resource should only be loaded once despite concurrent access
        verify(resourceLoader, times(1)).getResource("test.txt");
    }

    // Helper Methods

    private void setupMockResource(String content) throws IOException {
        when(resourceLoader.getResource(anyString())).thenReturn(mockResource);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getContentAsString(any())).thenReturn(content);
    }

    private void setupMultiplePrompts() throws IOException {
        // Setup for pass 1
        Resource mockResource1 = mock(Resource.class);
        when(promptProperties.getSceneDetectionPass1Path()).thenReturn("pass1.txt");
        when(resourceLoader.getResource("pass1.txt")).thenReturn(mockResource1);
        when(mockResource1.exists()).thenReturn(true);
        when(mockResource1.getContentAsString(any())).thenReturn("Pass 1 prompt");
        
        // Setup for pass 2
        Resource mockResource2 = mock(Resource.class);
        when(promptProperties.getSceneDetectionPass2Path()).thenReturn("pass2.txt");
        when(resourceLoader.getResource("pass2.txt")).thenReturn(mockResource2);
        when(mockResource2.exists()).thenReturn(true);
        when(mockResource2.getContentAsString(any())).thenReturn("Pass 2 prompt");
        
        // Setup for RAG
        Resource mockResource3 = mock(Resource.class);
        when(promptProperties.getRagAnswerGenerationPath()).thenReturn("rag.txt");
        when(resourceLoader.getResource("rag.txt")).thenReturn(mockResource3);
        when(mockResource3.exists()).thenReturn(true);
        when(mockResource3.getContentAsString(any())).thenReturn("RAG prompt");
    }
}