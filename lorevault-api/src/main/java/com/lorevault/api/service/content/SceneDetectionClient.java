package com.lorevault.api.service.content;

import com.lorevault.api.service.shared.PromptLoaderService;
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
 * Supports both single-pass (legacy) and two-pass scene detection workflows.
 * Encapsulates the AI model configuration, prompt loading, and retry logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SceneDetectionClient {
    
    @Qualifier("nlpSmall")
    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;
    
    @Qualifier("llmRetryTemplate")
    private final RetryTemplate retryTemplate;

    @Value("${lorevault.ai.models.nlp-small.model:llama-3.1-8b-instant}")
    private String currentModelId;

    /**
     * Perform two-pass scene detection on chapter text.
     * Pass 1: Initial scene segmentation and rich hints.
     * Pass 2: Schema normalization of pass 1 results.
     * 
     * @param chapterText The full chapter text to analyze
     * @return Raw XML response from Pass 2 (normalized scenes)
     * @throws RuntimeException if either pass fails after retries
     */
    public String detectScenesTwoPass(String chapterText) {
        log.debug("[LLM] Starting two-pass scene detection: inputLength={} chars, model={}", 
                 chapterText == null ? 0 : chapterText.length(), currentModelId);
        
        // Pass 1: Initial scene detection with rich hints
        String pass1Result = detectScenesPass1(chapterText);
        log.debug("[LLM] Pass 1 completed, result length={} chars", pass1Result.length());
        
        // Pass 2: Schema normalization using pass 1 results
        String pass2Result = detectScenesPass2(pass1Result);
        log.debug("[LLM] Pass 2 completed, final result length={} chars", pass2Result.length());
        
        return pass2Result;
    }

    /**
     * Pass 1: Initial scene segmentation with rich hints.
     * 
     * @param chapterText The full chapter text to analyze
     * @return Raw XML response from Pass 1
     * @throws RuntimeException if pass 1 fails after retries
     */
    public String detectScenesPass1(String chapterText) {
        PromptTemplate template = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        String systemPrompt = template.render(Map.of());
        
        return executeSceneDetectionCall("Pass 1", systemPrompt, chapterText);
    }

    /**
     * Pass 2: Schema normalization of Pass 1 results.
     * 
     * @param pass1XmlResult The XML output from Pass 1
     * @return Raw XML response from Pass 2 (normalized)
     * @throws RuntimeException if pass 2 fails after retries  
     */
    public String detectScenesPass2(String pass1XmlResult) {
        PromptTemplate template = promptLoaderService.getSceneDetectionPass2PromptTemplate();
        String systemPrompt = template.render(Map.of());
        
        return executeSceneDetectionCall("Pass 2", systemPrompt, pass1XmlResult);
    }

    /**
     * Legacy method: single-pass scene detection using the v2 prompt.
     * 
     * @param chapterText The full chapter text to analyze
     * @return Raw XML response from the AI model
     * @throws RuntimeException if all retry attempts fail
     */
    public String detectScenes(String chapterText) {
        // Load the system prompt (instructions) from resources
        PromptTemplate template = promptLoaderService.getSceneDetectionPromptTemplate();
        String systemPrompt = template.render(Map.of());
        
        return executeSceneDetectionCall("Single-pass", systemPrompt, chapterText);
    }

    /**
     * Execute a scene detection call with retry logic.
     * Common implementation for both passes and legacy single-pass.
     * 
     * @param passName Descriptive name for logging (e.g., "Pass 1", "Pass 2", "Single-pass")
     * @param systemPrompt The system prompt to use
     * @param userInput The user input (chapter text or pass 1 results)
     * @return Raw XML response from the AI model
     * @throws RuntimeException if all retry attempts fail
     */
    private String executeSceneDetectionCall(String passName, String systemPrompt, String userInput) {
        log.debug("[LLM] {} scene detection request: inputLength={} chars, model={}", 
                 passName, userInput == null ? 0 : userInput.length(), currentModelId);
        log.trace("[LLM] System prompt ({} chars): {}", systemPrompt.length(), systemPrompt);
        
        // Create options for consistent, deterministic results
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .temperature(0.1)
            .topP(0.9)
            .maxTokens(6000)
            .build();
            
        final String safeInput = userInput == null ? "" : userInput;
        
        try {
            long start = System.nanoTime();
            return retryTemplate.execute(retryContext -> {
                int retryCount = retryContext.getRetryCount();
                String attemptMsg = retryCount > 0 ? " (retry=" + retryCount + ")" : "";
                log.debug("[LLM] Calling model={} for {}{}", currentModelId, passName, attemptMsg);
                
                String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(safeInput)
                    .options(options)
                    .call()
                    .content();
                    
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                if (response == null || response.trim().isEmpty()) {
                    log.warn("[LLM] Empty response from {} (elapsed={}ms) model={}", 
                            passName, elapsedMs, currentModelId);
                    throw new RuntimeException("Empty response from " + currentModelId + " during " + passName);
                }
                
                int len = response.length();
                String preview = response.substring(0, Math.min(400, len)).replaceAll("\n", "\\n");
                log.debug("[LLM] {} response length={} elapsed={}ms model={}", 
                         passName, len, elapsedMs, currentModelId);
                log.trace("[LLM] Full raw response:{}\n{}", System.lineSeparator(), response);
                log.debug("[LLM] Response preview (first {} chars): {}", preview.length(), preview);
                
                return response;
                
            }, recoveryContext -> {
                Throwable lastError = recoveryContext.getLastThrowable();
                String errorMsg = lastError != null ? lastError.getMessage() : "Unknown error";
                log.error("[LLM] {} failed after {} retries: {}", 
                         passName, recoveryContext.getRetryCount(), errorMsg);
                throw new RuntimeException(passName + " scene detection failed permanently after multiple attempts: " + errorMsg,
                        recoveryContext.getLastThrowable());
            });
            
        } catch (Exception e) {
            log.error("[LLM] Unexpected error during {} scene detection: {}", passName, e.getMessage(), e);
            throw new RuntimeException(passName + " scene detection failed: " + e.getMessage(), e);
        }
    }
}
