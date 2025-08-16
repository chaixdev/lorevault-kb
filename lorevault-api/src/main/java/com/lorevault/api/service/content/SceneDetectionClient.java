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
    
    @Value("${lorevault.ai.models.nlp-big.model:unknown}")
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
        log.debug("[LLM] Scene detection request: inputLength={} chars, model={}", chapterText == null ? 0 : chapterText.length(), currentModelId);
        log.trace("[LLM] System prompt ({} chars): {}", systemPrompt.length(), systemPrompt);
        // Create options for consistent, deterministic results
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .temperature(0.1)
            .topP(0.9)
            .maxTokens(6000)
            .build();
        final String safeText = chapterText == null ? "" : chapterText;
        try {
            long start = System.nanoTime();
            return retryTemplate.execute(retryContext -> {
                int retryCount = retryContext.getRetryCount();
                String attemptMsg = retryCount > 0 ? " (retry=" + retryCount + ")" : "";
                log.debug("[LLM] Calling model={}{}", currentModelId, attemptMsg);
                String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(safeText)
                    .options(options)
                    .call()
                    .content();
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                if (response == null || response.trim().isEmpty()) {
                    log.warn("[LLM] Empty response (elapsed={}ms) model={}", elapsedMs, currentModelId);
                    throw new RuntimeException("Empty response from " + currentModelId);
                }
                int len = response.length();
                String preview = response.substring(0, Math.min(400, len)).replaceAll("\n", "\\n");
                log.debug("[LLM] Raw response length={} elapsed={}ms model={}", len, elapsedMs, currentModelId);
                log.trace("[LLM] Full raw response:{}\n{}", System.lineSeparator(), response);
                log.debug("[LLM] Response preview (first {} chars): {}", preview.length(), preview);
                return response;
            }, recoveryContext -> {
                Throwable lastError = recoveryContext.getLastThrowable();
                String errorMsg = lastError != null ? lastError.getMessage() : "Unknown error";
                log.error("[LLM] All attempts failed after {} retries: {}", recoveryContext.getRetryCount(), errorMsg);
                throw new RuntimeException("Scene detection failed permanently after multiple attempts: " + errorMsg,
                        recoveryContext.getLastThrowable());
            });
        } catch (Exception e) {
            log.error("[LLM] Unexpected error during scene detection: {}", e.getMessage(), e);
            throw new RuntimeException("Scene detection failed: " + e.getMessage(), e);
        }
    }
}
