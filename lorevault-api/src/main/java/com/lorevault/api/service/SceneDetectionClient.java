package com.lorevault.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String currentModelId;
    
    /**
     * Calls the configured AI model to analyze chapter text and detect scene boundaries.
     * Uses system prompt + user message pattern with optimized parameters for consistent results.
     * Includes application-level retry logic for robustness.
     * 
     * @param chapterText The full chapter text to analyze
     * @return Raw XML response from the AI model
     * @throws RuntimeException if all retry attempts fail
     */
    public String detectScenes(String chapterText) {
        // Load the system prompt (instructions) from resources
        PromptTemplate template = promptLoaderService.getSceneDetectionPromptTemplate();
        String systemPrompt = template.render(Map.of()); // No variables needed for system prompt
        
        // Retry configuration for additional robustness beyond Spring AI's built-in retry
        int maxRetries = 3;
        long baseDelayMs = 2000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Calling {} for scene detection (attempt {}/{})", currentModelId, attempt, maxRetries);
                
                // Create options for consistent, deterministic results
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature(0.1)       // Very low temperature for consistent, deterministic results
                    .topP(0.9)              // Focused sampling - high quality tokens only
                    .maxTokens(6000)        // Sufficient for XML response with multiple scenes
                    .build();
                
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
                log.trace("Full LLM API response for scene detection (attempt {}): {}", attempt, response);
                
                return response;
                
            } catch (Exception e) {
                log.warn("Scene detection attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                
                if (attempt == maxRetries) {
                    log.error("All {} scene detection attempts failed for chapter text length {}", 
                             maxRetries, chapterText.length());
                    throw new RuntimeException("Scene detection failed after " + maxRetries + " attempts", e);
                }
                
                // Exponential backoff delay before retry
                long delayMs = baseDelayMs * (long) Math.pow(2, attempt - 1);
                try {
                    log.debug("Waiting {}ms before retry attempt {}", delayMs, attempt + 1);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Scene detection interrupted", ie);
                }
            }
        }
        
        // This should never be reached due to the throw in the catch block
        throw new RuntimeException("Unexpected end of retry loop");
    }
}
