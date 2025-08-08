package com.lorevault.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Client responsible for making AI calls for scene detection.
 * Encapsulates the AI model configuration, prompt loading, and retry logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SceneDetectionClient {
    
    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;
    
    @Qualifier("llmRetryTemplate")
    private final RetryTemplate retryTemplate;
    
    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String currentModelId;
    
    /**
     * Calls the configured AI model to analyze chapter text and detect scene boundaries.
     * Uses system prompt + user message pattern with optimized parameters for consistent results.
     * Leverages Spring RetryTemplate for robust retry handling.
     * 
     * @param chapterText The full chapter text to analyze
     * @return Raw XML response from the AI model
     * @throws RuntimeException if all retry attempts fail
     */
    public String detectScenes(String chapterText) {
        // Load the system prompt (instructions) from resources
        PromptTemplate template = promptLoaderService.getSceneDetectionPromptTemplate();
        String systemPrompt = template.render(Map.of()); // No variables needed for system prompt
        
        // Create options for consistent, deterministic results
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .temperature(0.1)       // Very low temperature for consistent, deterministic results
            .topP(0.9)              // Focused sampling - high quality tokens only
            .maxTokens(6000)        // Sufficient for XML response with multiple scenes
            .build();
        
        try {
            // Use RetryTemplate to handle retries with proper logging and backoff
            return retryTemplate.execute(retryContext -> {
                int retryCount = retryContext.getRetryCount();
                String attemptMsg = retryCount > 0 ? " (retry attempt " + retryCount + ")" : "";
                log.debug("Calling {} for scene detection{}", currentModelId, attemptMsg);
                
                // Call AI model with system prompt + user message pattern
                String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(chapterText)
                    .options(options)
                    .call()
                    .content();
                
                if (response == null || response.trim().isEmpty()) {
                    throw new RuntimeException("Empty response from " + currentModelId);
                }
                
                // Log full response at trace level for debugging
                log.trace("Full LLM API response for scene detection: {}", response);
                
                return response;
            }, recoveryContext -> {
                // This is the recovery callback, called when all retries are exhausted
                Throwable lastError = recoveryContext.getLastThrowable();
                String errorMsg = lastError != null ? lastError.getMessage() : "Unknown error";
                log.error("All scene detection attempts failed for text length {}: {}", 
                         chapterText.length(), errorMsg);
                
                throw new RuntimeException("Scene detection failed permanently after multiple attempts: " + errorMsg, 
                                          recoveryContext.getLastThrowable());
            });
        } catch (Exception e) {
            // This catches any exceptions not handled by the retry template
            log.error("Unexpected error during scene detection: {}", e.getMessage());
            throw new RuntimeException("Scene detection failed: " + e.getMessage(), e);
        }
    }
}
