package com.lorevault.api.service.content;

import com.lorevault.api.configuration.properties.LoreVaultPromptProperties;
import com.lorevault.api.service.shared.PromptLoaderService;
import com.lorevault.api.service.ingestion.LlmCallLoggingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Client responsible for making AI calls for scene detection.
 * Supports both single-pass (legacy) and two-pass scene detection workflows.
 * Encapsulates the AI model configuration, prompt loading, and retry logic.
 */
@Component
@Slf4j
public class SceneDetectionClient {
    
    private final ChatClient nlpSmallChatClient;
    private final ChatClient nlpBigChatClient;
    private final PromptLoaderService promptLoaderService;
    private final LoreVaultPromptProperties promptProperties;
    private final LlmCallLoggingService llmLog;
    
    @Qualifier("llmRetryTemplate")
    private final RetryTemplate retryTemplate;

    @Value("${lorevault.ai.models.nlp-small.model:llama-3.1-8b-instant}")
    private String nlpSmallModelId;
    @Value("${lorevault.ai.models.nlp-big.model:llama-3.3-70b-versatile}")
    private String nlpBigModelId;

    public SceneDetectionClient(
            @Qualifier("nlpSmall") ChatClient nlpSmallChatClient,
            @Qualifier("nlpBig") ChatClient nlpBigChatClient,
            PromptLoaderService promptLoaderService,
            LoreVaultPromptProperties promptProperties,
            @Qualifier("llmRetryTemplate") RetryTemplate retryTemplate,
            LlmCallLoggingService llmLog) {
        this.nlpSmallChatClient = nlpSmallChatClient;
        this.nlpBigChatClient = nlpBigChatClient;
        this.promptLoaderService = promptLoaderService;
        this.promptProperties = promptProperties;
        this.retryTemplate = retryTemplate;
        this.llmLog = llmLog;
    }

    /**
     * Perform two-pass scene detection on chapter text.
     * Pass 1: Initial scene segmentation and rich hints.
     * Pass 2: Schema normalization of pass 1 results.
     * 
     * @param chapterText The full chapter text to analyze
     * @return Raw XML response from Pass 2 (normalized scenes)
     * @throws RuntimeException if either pass fails after retries
     */
    public String detectScenesTwoPass(UUID jobId, String chapterText) {
        String pass1ModelId = getModelIdForPass("pass1");
        log.debug("[LLM] Starting two-pass scene detection: inputLength={} chars, pass1Model={}", 
                 chapterText == null ? 0 : chapterText.length(), pass1ModelId);
        
        // Pass 1: Initial scene detection with rich hints
        String pass1Result = detectScenesPass1(jobId, chapterText);
        log.debug("[LLM] Pass 1 completed, result length={} chars", pass1Result.length());
        
        // Pass 2: Schema normalization using pass 1 results
        String pass2Result = detectScenesPass2(jobId, pass1Result);
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
    public String detectScenesPass1(UUID jobId, String chapterText) {
        PromptTemplate template = promptLoaderService.getSceneDetectionPass1PromptTemplate();
        String systemPrompt = template.render(Map.of());
        
        String modelId = promptProperties.getSceneDetectionPass1Model();
        ChatClient chatClient = getChatClientForModel(modelId);
        String actualModelId = getModelIdForPass("pass1");
        
        return executeSceneDetectionCall(jobId, "scene-detection-pass1", systemPrompt, chapterText, chatClient, actualModelId);
    }

    /**
     * Pass 2: Schema normalization of Pass 1 results.
     * 
     * @param pass1XmlResult The XML output from Pass 1
     * @return Raw XML response from Pass 2 (normalized)
     * @throws RuntimeException if pass 2 fails after retries  
     */
    public String detectScenesPass2(UUID jobId, String pass1XmlResult) {
        PromptTemplate template = promptLoaderService.getSceneDetectionPass2PromptTemplate();
        String systemPrompt = template.render(Map.of());
        
        String modelId = promptProperties.getSceneDetectionPass2Model();
        ChatClient chatClient = getChatClientForModel(modelId);
        String actualModelId = getModelIdForPass("pass2");
        
        return executeSceneDetectionCall(jobId, "scene-detection-pass2", systemPrompt, pass1XmlResult, chatClient, actualModelId);
    }

    /**
     * Pass 2 (triad): use user template to send prev/curr/next scene slices.
     * @param jobId Job context
     * @param systemPrompt The triad system prompt content
     * @param userVariables Variables for the user template
     * @return Raw XML triad response
     */
    public String detectScenesPass2Triad(UUID jobId, String systemPrompt, Map<String, Object> userVariables) {
        PromptTemplate userTemplate = promptLoaderService.getSceneDetectionPass2UserTemplate();
        String userInput = userTemplate.render(userVariables);

        String modelId = promptProperties.getSceneDetectionPass2Model();
        ChatClient chatClient = getChatClientForModel(modelId);
        String actualModelId = getModelIdForPass("pass2");

        return executeSceneDetectionCall(jobId, "scene-detection-pass2", systemPrompt, userInput, chatClient, actualModelId);
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
        
        // Legacy uses small model by default
        return executeSceneDetectionCall(null, "scene-detection-single-pass", systemPrompt, chapterText, nlpSmallChatClient, nlpSmallModelId);
    }

    /**
     * Get the appropriate ChatClient for the specified model slot.
     */
    private ChatClient getChatClientForModel(String modelSlot) {
        return switch (modelSlot) {
            case "nlp-big" -> nlpBigChatClient;
            case "nlp-small" -> nlpSmallChatClient;
            default -> nlpSmallChatClient; // Default fallback
        };
    }
    
    /**
     * Get the actual model ID for the specified pass.
     */
    private String getModelIdForPass(String pass) {
        return switch (pass) {
            case "pass1" -> "nlp-big".equals(promptProperties.getSceneDetectionPass1Model()) ? nlpBigModelId : nlpSmallModelId;
            case "pass2" -> "nlp-big".equals(promptProperties.getSceneDetectionPass2Model()) ? nlpBigModelId : nlpSmallModelId;
            default -> nlpSmallModelId; // Default fallback
        };
    }

    /**
     * Execute a scene detection call with retry logic.
     * Common implementation for both passes and legacy single-pass.
     * 
     * @param passName Descriptive name for logging (e.g., "Pass 1", "Pass 2", "Single-pass")
     * @param systemPrompt The system prompt to use
     * @param userInput The user input (chapter text or pass 1 results)
     * @param chatClient The ChatClient to use for this call
     * @param modelId The model ID for logging
     * @return Raw XML response from the AI model
     * @throws RuntimeException if all retry attempts fail
     */
    private String executeSceneDetectionCall(UUID jobId, String step, String systemPrompt, String userInput, ChatClient chatClient, String modelId) {
        log.debug("[LLM] {} request: inputLength={} chars, model={}", 
                 step, userInput == null ? 0 : userInput.length(), modelId);
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
                log.debug("[LLM] Calling model={} for {}{}", modelId, step, attemptMsg);
                
                String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(safeInput)
                    .options(options)
                    .call()
                    .content();
                    
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                if (response == null || response.trim().isEmpty()) {
                    log.warn("[LLM] Empty response from {} (elapsed={}ms) model={}", 
                            step, elapsedMs, modelId);
                    throw new RuntimeException("Empty response from " + modelId + " during " + step);
                }
                
                int len = response.length();
                String preview = response.substring(0, Math.min(400, len)).replaceAll("\n", "\\n");
                log.debug("[LLM] {} response length={} elapsed={}ms model={}", 
                         step, len, elapsedMs, modelId);
                log.trace("[LLM] Full raw response:{}\n{}", System.lineSeparator(), response);
                log.debug("[LLM] Response preview (first {} chars): {}", preview.length(), preview);

                // Log LLM call record
                llmLog.logCall(
                    jobId,
                    step,
                    "openai-compatible", // provider abstraction
                    modelId,
                    options.getTemperature(),
                    options.getTopP(),
                    options.getMaxTokens(),
                    // Prompt metadata
                    step.equals("scene-detection-pass1") ? promptProperties.getSceneDetectionPass1Path() : promptProperties.getSceneDetectionPass2Path(),
                    systemPrompt,
                    safeInput.length() <= 1000 ? safeInput : safeInput.substring(0, 1000),
                    response,
                    elapsedMs,
                    estimateTokens(safeInput),
                    estimateTokens(response)
                );
                
                return response;
                
            }, recoveryContext -> {
                Throwable lastError = recoveryContext.getLastThrowable();
                String errorMsg = lastError != null ? lastError.getMessage() : "Unknown error";
                log.error("[LLM] {} failed after {} retries: {}", 
                         step, recoveryContext.getRetryCount(), errorMsg);
                throw new RuntimeException(step + " scene detection failed permanently after multiple attempts: " + errorMsg,
                        recoveryContext.getLastThrowable());
            });
            
        } catch (Exception e) {
            log.error("[LLM] Unexpected error during {} scene detection: {}", step, e.getMessage(), e);
            throw new RuntimeException(step + " scene detection failed: " + e.getMessage(), e);
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // Simple heuristic: 1 token ~ 4 chars
        return Math.max(1, text.length() / 4);
    }
}
