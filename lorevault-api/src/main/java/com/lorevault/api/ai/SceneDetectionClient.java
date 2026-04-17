package com.lorevault.api.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.config.LoreVaultPromptProperties;
import com.lorevault.api.config.LoreVaultModelsProperties;
import com.lorevault.api.ingestion.LlmCallLoggingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client responsible for making AI calls for scene detection.
 * Supports both single-pass (legacy) and two-pass scene detection workflows.
 * Encapsulates the AI model configuration, prompt loading, and retry logic.
 */
@Component
public class SceneDetectionClient {
    private static final Logger log = LoggerFactory.getLogger(SceneDetectionClient.class);
    
    private final ChatClient nlpSmallChatClient;
    private final ChatClient nlpBigChatClient;
    private final PromptRepository promptRepository;
    private final LoreVaultPromptProperties promptProperties;
    private final LoreVaultModelsProperties modelProperties;
    private final LlmCallLoggingService llmLog;
    private final ObjectMapper objectMapper;
    
    @Qualifier("llmRetryTemplate")
    private final RetryTemplate retryTemplate;

    @Value("${lorevault.ai.models.nlp-small.model:openai/gpt-oss-120b}")
    private String nlpSmallModelId;
    @Value("${lorevault.ai.models.nlp-big.model:openai/gpt-oss-120b}")
    private String nlpBigModelId;

    public SceneDetectionClient(
            @Qualifier("nlpSmall") ChatClient nlpSmallChatClient,
            @Qualifier("nlpBig") ChatClient nlpBigChatClient,
            PromptRepository promptRepository,
            LoreVaultPromptProperties promptProperties,
            LoreVaultModelsProperties modelProperties,
            @Qualifier("llmRetryTemplate") RetryTemplate retryTemplate,
            LlmCallLoggingService llmLog,
            ObjectMapper objectMapper) {
        this.nlpSmallChatClient = nlpSmallChatClient;
        this.nlpBigChatClient = nlpBigChatClient;
        this.promptRepository = promptRepository;
        this.promptProperties = promptProperties;
        this.modelProperties = modelProperties;
        this.retryTemplate = retryTemplate;
        this.llmLog = llmLog;
        this.objectMapper = objectMapper;
    }

    private static final double SEGMENTATION_INPUT_BUDGET_RATIO = 0.70d;

    public SegmentationBudgetCheck evaluateSegmentationBudget(String chapterText) {
        PromptTemplate template = promptRepository.get("chapter-segmentation");
        String systemPrompt = template.render(Map.of());

        String modelSlot = promptProperties.getChapterSegmentationModel();
        LoreVaultModelsProperties.ModelProperties cfg = getModelProperties(modelSlot);

        int estimatedPromptTokens = estimateTokens(systemPrompt);
        int estimatedInputTokens = estimateTokens(chapterText);
        int estimatedTotalInput = estimatedPromptTokens + estimatedInputTokens;
        int maxContextTokens = cfg.maxContextTokens();
        int usableInputBudget = (int) Math.floor(maxContextTokens * SEGMENTATION_INPUT_BUDGET_RATIO);

        return new SegmentationBudgetCheck(
                modelSlot,
                maxContextTokens,
                usableInputBudget,
                estimatedPromptTokens,
                estimatedInputTokens,
                estimatedTotalInput,
                estimatedTotalInput <= usableInputBudget
        );
    }

    public String detectScenesTwoPass(UUID jobId, String chapterText) {
        String segmentationModelId = getModelIdForStage("segmentation");
        log.debug("[LLM] Starting two-stage scene detection: inputLength={} chars, segmentationModel={}", 
                 chapterText == null ? 0 : chapterText.length(), segmentationModelId);
        
        String segmentationResult = detectChapterSegmentation(jobId, chapterText);
        log.debug("[LLM] Chapter segmentation completed, result length={} chars", segmentationResult.length());
        
        String analysisResult = detectSceneAnalysis(jobId, segmentationResult);
        log.debug("[LLM] Scene analysis completed, final result length={} chars", analysisResult.length());
        
        return analysisResult;
    }

    public String detectChapterSegmentation(UUID jobId, String chapterText) {
        PromptTemplate template = promptRepository.get("chapter-segmentation");
        String systemPrompt = template.render(Map.of());
        
        String modelId = promptProperties.getChapterSegmentationModel();
        ChatClient chatClient = getChatClientForModel(modelId);
        String actualModelId = getModelIdForStage("segmentation");
        
        return executeSceneDetectionCall(jobId, "chapter-segmentation", systemPrompt, chapterText, chatClient, actualModelId);
    }

    public String detectSceneAnalysis(UUID jobId, String segmentationXmlResult) {
        PromptTemplate template = promptRepository.get("scene-analysis");
        String systemPrompt = template.render(Map.of());
        
        String modelId = promptProperties.getSceneAnalysisModel();
        ChatClient chatClient = getChatClientForModel(modelId);
        String actualModelId = getModelIdForStage("analysis");
        
        return executeSceneDetectionCall(jobId, "scene-analysis", systemPrompt, segmentationXmlResult, chatClient, actualModelId);
    }

    public <T> T detectSceneAnalysisTriad(UUID jobId, String systemPrompt, Map<String, Object> userVariables, Class<T> responseType) {
        PromptTemplate userTemplate = promptRepository.get("scene-analysis-user");
        String userInput = userTemplate.render(userVariables);

        String modelId = promptProperties.getSceneAnalysisModel();
        ChatClient chatClient = getChatClientForModel(modelId);
        String actualModelId = getModelIdForStage("analysis");

        return executeSceneDetectionStructuredCall(jobId, "scene-analysis", systemPrompt, userInput, chatClient, actualModelId, responseType);
    }

    /**
     * Legacy method: single-pass scene detection using the v2 prompt.
     * 
     * @param chapterText The full chapter text to analyze
     * @return Raw XML response from the AI model
     * @throws RuntimeException if all retry attempts fail
     */
    public String detectScenes(String chapterText) {
        PromptTemplate template = promptRepository.get("scene-analysis");
        String systemPrompt = template.render(Map.of());
        
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
    
    private String getModelIdForStage(String stage) {
        return switch (stage) {
            case "segmentation" -> "nlp-big".equals(promptProperties.getChapterSegmentationModel()) ? nlpBigModelId : nlpSmallModelId;
            case "analysis" -> "nlp-big".equals(promptProperties.getSceneAnalysisModel()) ? nlpBigModelId : nlpSmallModelId;
            default -> nlpSmallModelId;
        };
    }

    private LoreVaultModelsProperties.ModelProperties getModelProperties(String modelSlot) {
        return switch (modelSlot) {
            case "nlp-big" -> modelProperties.nlpBig();
            case "nlp-small" -> modelProperties.nlpSmall();
            default -> modelProperties.nlpSmall();
        };
    }

    /**
     * Execute a scene detection call with retry logic.
     * Common implementation for both stages and legacy single-pass.
     * 
     * @param step Descriptive name for logging (e.g., "chapter-segmentation", "scene-analysis", "scene-detection-single-pass")
     * @param systemPrompt The system prompt to use
     * @param userInput The user input (chapter text or chapter segmentation results)
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
                if (retryCount > 0) {
                    log.warn("[LLM] Retrying: jobId={}, step={}, model={}, attempt={}",
                            jobId, step, modelId, retryCount + 1);
                }
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
                    step.equals("chapter-segmentation") ? promptProperties.getChapterSegmentationPath() : promptProperties.getSceneAnalysisPath(),
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
                log.error("[LLM] Retry exhausted: jobId={}, step={}, model={}, retryCount={}, lastError={}",
                        jobId, step, modelId, recoveryContext.getRetryCount(), errorMsg);
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

    private <T> T executeSceneDetectionStructuredCall(UUID jobId, String step, String systemPrompt, String userInput,
                                                      ChatClient chatClient, String modelId, Class<T> responseType) {
        log.debug("[LLM] {} request: inputLength={} chars, model={}",
                step, userInput == null ? 0 : userInput.length(), modelId);

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
                if (retryCount > 0) {
                    log.warn("[LLM] Retrying: jobId={}, step={}, model={}, attempt={}",
                            jobId, step, modelId, retryCount + 1);
                }
                log.debug("[LLM] Calling model={} for {}{}", modelId, step, attemptMsg);

                T response = chatClient.prompt()
                        .system(systemPrompt)
                        .user(safeInput)
                        .options(options)
                        .call()
                        .entity(responseType);

                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                if (response == null) {
                    throw new RuntimeException("Empty structured response from " + modelId + " during " + step);
                }

                String responseBody = serializeStructuredResponse(response);

                llmLog.logCall(
                        jobId,
                        step,
                        "openai-compatible",
                        modelId,
                        options.getTemperature(),
                        options.getTopP(),
                        options.getMaxTokens(),
                    step.equals("chapter-segmentation") ? promptProperties.getChapterSegmentationPath() : promptProperties.getSceneAnalysisPath(),
                        systemPrompt,
                        safeInput.length() <= 1000 ? safeInput : safeInput.substring(0, 1000),
                        responseBody,
                        elapsedMs,
                        estimateTokens(safeInput),
                        estimateTokens(responseBody)
                );

                return response;
            }, recoveryContext -> {
                Throwable lastError = recoveryContext.getLastThrowable();
                String errorMsg = lastError != null ? lastError.getMessage() : "Unknown error";
                log.error("[LLM] Retry exhausted: jobId={}, step={}, model={}, retryCount={}, lastError={}",
                        jobId, step, modelId, recoveryContext.getRetryCount(), errorMsg);
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
        int charCount = text.length();
        int wordCount = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;

        int charsEstimate = (int) Math.ceil(charCount / 3.0d);
        int wordsEstimate = (int) Math.ceil(wordCount * 1.35d);

        return Math.max(1, Math.max(charsEstimate, wordsEstimate));
    }

    private String serializeStructuredResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException | RuntimeException e) {
            log.debug("[LLM] Structured response serialization failed, falling back to String.valueOf(): {}", e.getMessage());
            return String.valueOf(response);
        }
    }

    public record SegmentationBudgetCheck(
            String modelSlot,
            int maxContextTokens,
            int usableInputBudget,
            int estimatedPromptTokens,
            int estimatedInputTokens,
            int estimatedTotalInput,
            boolean isWithinBudget
    ) {}
}
