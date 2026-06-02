package com.lorevault.api.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.ai.infrastructure.LlmCallLoggingService;
import com.lorevault.api.ai.infrastructure.PromptName;
import com.lorevault.api.ai.infrastructure.PromptRepository;
import com.lorevault.api.config.LoreVaultPromptProperties;
import com.lorevault.api.config.LoreVaultModelsProperties;
import com.lorevault.api.ai.ModelSlot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lorevault.api.orchestration.pipeline.StageKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

/**
 * Client responsible for making AI calls for scene detection.
 * Encapsulates the AI model configuration, prompt loading, and retry logic.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    
    private final ChatClient nlpSmallChatClient;
    private final ChatClient nlpBigChatClient;
    private final PromptRepository promptRepository;
    private final LoreVaultPromptProperties promptProperties;
    private final LoreVaultModelsProperties modelProperties;
    private final LlmCallLoggingService llmLog;
    private final ObjectMapper objectMapper;
    
    @Qualifier("llmRetryTemplate")
    private final RetryTemplate retryTemplate;

    public LlmClient(ChatClient nlpSmallChatClient,
                     ChatClient nlpBigChatClient,
                     PromptRepository promptRepository,
                     LoreVaultPromptProperties promptProperties,
                     LoreVaultModelsProperties modelProperties,
                     LlmCallLoggingService llmLog,
                     ObjectMapper objectMapper,
                     @Qualifier("llmRetryTemplate") RetryTemplate retryTemplate) {
        this.nlpSmallChatClient = nlpSmallChatClient;
        this.nlpBigChatClient = nlpBigChatClient;
        this.promptRepository = promptRepository;
        this.promptProperties = promptProperties;
        this.modelProperties = modelProperties;
        this.llmLog = llmLog;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
    }

    private static final double SEGMENTATION_INPUT_BUDGET_RATIO = 0.70d;

    public SegmentationBudgetCheck evaluateSegmentationBudget(String chapterText) {
        PromptTemplate template = promptRepository.get(PromptName.CHAPTER_SEGMENTATION);
        String systemPrompt = template.render(Map.of());

        String modelSlotStr = promptProperties.getChapterSegmentationModel();
        ModelSlot modelSlot = ModelSlot.NLP_BIG.slotName().equals(modelSlotStr) ? ModelSlot.NLP_BIG : ModelSlot.NLP_SMALL;
        LoreVaultModelsProperties.ModelProperties cfg = getModelProperties(modelSlot);

        int estimatedPromptTokens = estimateTokens(systemPrompt);
        int estimatedInputTokens = estimateTokens(chapterText);
        int estimatedTotalInput = estimatedPromptTokens + estimatedInputTokens;
        int maxContextTokens = cfg.maxContextTokens();
        int usableInputBudget = (int) Math.floor(maxContextTokens * SEGMENTATION_INPUT_BUDGET_RATIO);

        return new SegmentationBudgetCheck(
                modelSlotStr,
                maxContextTokens,
                usableInputBudget,
                estimatedPromptTokens,
                estimatedInputTokens,
                estimatedTotalInput,
                estimatedTotalInput <= usableInputBudget
        );
    }

    public String detectChapterSegmentation(UUID jobId, String chapterText, double temperature) {
        PromptTemplate template = promptRepository.get(PromptName.CHAPTER_SEGMENTATION);
        String systemPrompt = template.render(Map.of());
        
        PromptTemplate userTemplate = promptRepository.get(PromptName.CHAPTER_SEGMENTATION_USER);
        String userInput = userTemplate.render(Map.of("chapter_text", chapterText));
        
        String modelSlotStr = promptProperties.getChapterSegmentationModel();
        ModelSlot modelSlot = ModelSlot.NLP_BIG.slotName().equals(modelSlotStr) ? ModelSlot.NLP_BIG : ModelSlot.NLP_SMALL;
        ChatClient chatClient = getChatClientForModel(modelSlot);
        String actualModelId = getModelIdForStage(StageKey.SCENE_SEGMENTATION);
        
        return executeSceneDetectionCall(jobId, StageKey.SCENE_SEGMENTATION, systemPrompt, userInput, chatClient, actualModelId, temperature);
    }

    public <T> T detectSceneAnalysisTriad(UUID jobId, String systemPrompt, Map<String, Object> userVariables,
                                          double temperature, Class<T> responseType) {
        PromptTemplate userTemplate = promptRepository.get(PromptName.SCENE_ANALYSIS_USER);
        String userInput = userTemplate.render(userVariables);

        String modelSlotStr = promptProperties.getSceneAnalysisModel();
        ModelSlot modelSlot = ModelSlot.NLP_BIG.slotName().equals(modelSlotStr) ? ModelSlot.NLP_BIG : ModelSlot.NLP_SMALL;
        ChatClient chatClient = getChatClientForModel(modelSlot);
        String actualModelId = getModelIdForStage(StageKey.CHAPTER_EVENT_CONSOLIDATION);

        return executeSceneDetectionStructuredCall(
                jobId,
                StageKey.CHAPTER_EVENT_CONSOLIDATION,
                promptProperties.getSceneAnalysisPath(),
                systemPrompt,
                userInput,
                chatClient,
                actualModelId,
                temperature,
                responseType
        );
    }

    /**
     * Executes a single event co-reference window call.
     * Sends the system prompt (loaded from event-coref-system.st) plus the rendered
     * user template (event-coref-usertemplate.st) and returns a structured response.
     *
     * @param jobId     correlation id for logging
     * @param userInput rendered user message (window of 2-3 mention descriptions)
     * @return structured co-reference judgment response
     */
    public EventCorefModels.CorefWindowResponse runEventCoref(
            UUID jobId,
            String userInput
    ) {
        PromptTemplate systemTemplate = promptRepository.get(PromptName.EVENT_COREF_SYSTEM);
        String systemPrompt = systemTemplate.render(Map.of());

        String modelSlotStr = promptProperties.getSceneAnalysisModel();
        ModelSlot modelSlot = ModelSlot.NLP_BIG.slotName().equals(modelSlotStr) ? ModelSlot.NLP_BIG : ModelSlot.NLP_SMALL;
        ChatClient chatClient = getChatClientForModel(modelSlot);
        String actualModelId = getModelIdForStage(StageKey.CHAPTER_EVENT_CONSOLIDATION);

        return executeSceneDetectionStructuredCall(
                jobId,
                StageKey.CHAPTER_EVENT_CONSOLIDATION,
                promptProperties.getEventCorefSystemPath(),
                systemPrompt,
                userInput,
                chatClient,
                actualModelId,
                0.1,
                EventCorefModels.CorefWindowResponse.class
        );
    }

    /**
     * Executes a single ChapterEvent semantic merge verification call.
     * Uses the {@code event-merge-system.st} prompt and structured JSON response binding.
     */
    public EventMergeModels.EventMergePairResponse runEventMergeVerification(
            UUID jobId,
            String userInput
    ) {
        PromptTemplate systemTemplate = promptRepository.get(PromptName.EVENT_MERGE_SYSTEM);
        String systemPrompt = systemTemplate.render(Map.of());

        ChatClient chatClient = getChatClientForModel(ModelSlot.NLP_SMALL);

        return executeSceneDetectionStructuredCall(
                jobId,
                StageKey.CHAPTER_EVENT_CONSOLIDATION,
                promptProperties.getEventMergeSystemPath(),
                systemPrompt,
                userInput,
                chatClient,
                modelProperties.nlpSmall().model(),
                0.1,
                EventMergeModels.EventMergePairResponse.class
        );
    }

    public String getEventCorefModelId() {
        return getModelIdForStage(StageKey.CHAPTER_EVENT_CONSOLIDATION);
    }

    /**
     * Get the appropriate ChatClient for the specified model slot.
     */
    private ChatClient getChatClientForModel(ModelSlot modelSlot) {
        return switch (modelSlot) {
            case NLP_BIG -> nlpBigChatClient;
            case NLP_SMALL -> nlpSmallChatClient;
        };
    }
    
    private String getModelIdForStage(StageKey stage) {
        return switch (stage) {
            case SCENE_SEGMENTATION -> ModelSlot.NLP_BIG.slotName().equals(promptProperties.getChapterSegmentationModel()) ? modelProperties.nlpBig().model() : modelProperties.nlpSmall().model();
            case CHAPTER_EVENT_CONSOLIDATION -> ModelSlot.NLP_BIG.slotName().equals(promptProperties.getSceneAnalysisModel()) ? modelProperties.nlpBig().model() : modelProperties.nlpSmall().model();
            default -> modelProperties.nlpSmall().model();
        };
    }

    private LoreVaultModelsProperties.ModelProperties getModelProperties(ModelSlot modelSlot) {
        return switch (modelSlot) {
            case NLP_BIG -> modelProperties.nlpBig();
            case NLP_SMALL -> modelProperties.nlpSmall();
        };
    }

    /**
     * Execute a scene detection call with retry logic.
     *
     * @param step Descriptive name for logging (e.g., "chapter-segmentation")
     * @param systemPrompt The system prompt to use
     * @param userInput The user input (chapter text)
     * @param chatClient The ChatClient to use for this call
     * @param modelId The model ID for logging
     * @return Raw XML response from the AI model
     * @throws RuntimeException if all retry attempts fail
     */
    private String executeSceneDetectionCall(UUID jobId, StageKey stage, String systemPrompt, String userInput, ChatClient chatClient, String modelId, double temperature) {
        String step = stage.name().toLowerCase().replace('_', '-');
        log.debug("[LLM] {} request: inputLength={} chars, model={}", 
                 step, userInput == null ? 0 : userInput.length(), modelId);
        log.trace("[LLM] System prompt ({} chars) sha256={}", systemPrompt.length(), sha256prefix(systemPrompt));
        
        final String safeInput = userInput == null ? "" : userInput;
        UUID callId = UUID.randomUUID();
        final Integer[] promptTokensHolder = new Integer[1];
        final Integer[] completionTokensHolder = new Integer[1];
        
        try {
            long start = System.nanoTime();
            String response = retryTemplate.execute(retryContext -> {
                int retryCount = retryContext.getRetryCount();
                double attemptTemp = temperature + (retryCount * 0.1);
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature(attemptTemp)
                    .topP(0.9)
                    .maxTokens(6000)
                    .build();
                String attemptMsg = retryCount > 0 ? " (retry=" + retryCount + ")" : "";
                if (retryCount > 0) {
                    log.warn("[LLM] Retrying: jobId={}, step={}, model={}, attempt={}",
                            jobId, step, modelId, retryCount + 1);
                }
                log.debug("[LLM] Calling model={} for {}{}", modelId, step, attemptMsg);
                
                var callSpec = chatClient.prompt()
                    .system(systemPrompt)
                    .user(safeInput)
                    .options(options)
                    .call();
                String responseContent = callSpec.content();
                    
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                if (responseContent == null || responseContent.trim().isEmpty()) {
                    log.warn("[LLM] Empty response from {} (elapsed={}ms) model={}", 
                            step, elapsedMs, modelId);
                    throw new RuntimeException("Empty response from " + modelId + " during " + step);
                }
                
                int len = responseContent.length();
                log.debug("[LLM] {} response length={} elapsed={}ms model={}", 
                         step, len, elapsedMs, modelId);
                String preview = responseContent.substring(0, Math.min(100, len)).replaceAll("\n", "\\n");
                log.debug("[LLM] Response preview (first {} chars): {}", preview.length(), preview);

                // Capture actual token counts from ChatResponse if available
                var chatResponse = callSpec.chatResponse();
                if (chatResponse != null && chatResponse.getMetadata() != null) {
                    var usage = chatResponse.getMetadata().getUsage();
                    if (usage != null) {
                        promptTokensHolder[0] = usage.getPromptTokens();
                        completionTokensHolder[0] = usage.getCompletionTokens();
                    }
                }

                return responseContent;
                
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
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            
            persistLlmCallSafely(
                    callId,
                    jobId,
                    stage,
                    modelId,
                    temperature,
                    0.9,
                    6000,
                    stage == StageKey.SCENE_SEGMENTATION ? promptProperties.getChapterSegmentationPath() : promptProperties.getSceneAnalysisPath(),
                    systemPrompt,
                    safeInput,
                    response,
                    elapsedMs,
                    promptTokensHolder[0],
                    completionTokensHolder[0]
            );
            return response;
            
        } catch (Exception e) {
            log.error("[LLM] Unexpected error during {} scene detection: {}", step, e.getMessage(), e);
            throw new RuntimeException(step + " scene detection failed: " + e.getMessage(), e);
        }
    }

    private <T> T executeSceneDetectionStructuredCall(UUID jobId, StageKey stage, String promptTemplateId,
                                                        String systemPrompt, String userInput,
                                                        ChatClient chatClient, String modelId,
                                                        double temperature, Class<T> responseType) {
        String step = stage.name().toLowerCase().replace('_', '-');
        log.debug("[LLM] {} request: inputLength={} chars, model={}",
                step, userInput == null ? 0 : userInput.length(), modelId);

        final String safeInput = userInput == null ? "" : userInput;
        UUID callId = UUID.randomUUID();
        final Integer[] promptTokensHolder = new Integer[1];
        final Integer[] completionTokensHolder = new Integer[1];

        try {
            long start = System.nanoTime();
            T response = retryTemplate.execute(retryContext -> {
                int retryCount = retryContext.getRetryCount();
                double attemptTemp = temperature + (retryCount * 0.1);
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .temperature(attemptTemp)
                        .topP(0.9)
                        .maxTokens(6000)
                        .build();
                String attemptMsg = retryCount > 0 ? " (retry=" + retryCount + ")" : "";
                if (retryCount > 0) {
                    log.warn("[LLM] Retrying: jobId={}, step={}, model={}, attempt={}",
                            jobId, step, modelId, retryCount + 1);
                }
                log.debug("[LLM] Calling model={} for {}{}", modelId, step, attemptMsg);

                var callSpec = chatClient.prompt()
                        .system(systemPrompt)
                        .user(safeInput)
                        .options(options)
                        .call();
                T structuredResponse = callSpec.entity(responseType);

                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                if (structuredResponse == null) {
                    throw new RuntimeException("Empty structured response from " + modelId + " during " + step);
                }

                // Capture actual token counts from ChatResponse if available
                var chatResponse = callSpec.chatResponse();
                if (chatResponse != null && chatResponse.getMetadata() != null) {
                    var usage = chatResponse.getMetadata().getUsage();
                    if (usage != null) {
                        promptTokensHolder[0] = usage.getPromptTokens();
                        completionTokensHolder[0] = usage.getCompletionTokens();
                    }
                }

                return structuredResponse;
            }, recoveryContext -> {
                Throwable lastError = recoveryContext.getLastThrowable();
                String errorMsg = lastError != null ? lastError.getMessage() : "Unknown error";
                log.error("[LLM] Retry exhausted: jobId={}, step={}, model={}, retryCount={}, lastError={}",
                        jobId, step, modelId, recoveryContext.getRetryCount(), errorMsg);
                throw new RuntimeException(step + " scene detection failed permanently after multiple attempts: " + errorMsg,
                        recoveryContext.getLastThrowable());
            });
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String responseBody = serializeStructuredResponse(response);
            persistLlmCallSafely(
                    callId,
                    jobId,
                    stage,
                    modelId,
                    temperature,
                    0.9,
                    6000,
                    promptTemplateId,
                    systemPrompt,
                    safeInput,
                    responseBody,
                    elapsedMs,
                    promptTokensHolder[0],
                    completionTokensHolder[0]
            );
            return response;
        } catch (Exception e) {
            log.error("[LLM] Unexpected error during {} scene detection: {}", step, e.getMessage(), e);
            throw new RuntimeException(step + " scene detection failed: " + e.getMessage(), e);
        }
    }

    private void persistLlmCallSafely(UUID callId,
                                      UUID jobId,
                                      StageKey stage,
                                      String modelId,
                                      Double temperature,
                                      Double topP,
                                      Integer maxTokens,
                                      String promptTemplateId,
                                      String systemPrompt,
                                      String input,
                                      String responseBody,
                                      long elapsedMs,
                                      Integer inputTokensActual,
                                      Integer outputTokensActual) {
        String step = stage.name().toLowerCase().replace('_', '-');
        try {
            Integer inputTokens = inputTokensActual != null ? inputTokensActual : estimateTokens(input);
            Integer outputTokens = outputTokensActual != null ? outputTokensActual : estimateTokens(responseBody);
            Boolean tokensEstimated = (inputTokensActual == null || outputTokensActual == null);
            llmLog.logCall(
                    callId,
                    jobId,
                    stage,
                    "openai-compatible",
                    modelId,
                    temperature,
                    topP,
                    maxTokens,
                    promptTemplateId,
                    systemPrompt,
                    input,
                    responseBody,
                    elapsedMs,
                    inputTokens,
                    outputTokens,
                    tokensEstimated
            );
        } catch (Exception loggingError) {
            log.warn("[LLM] Call logging failed after successful model response: jobId={}, step={}, model={}, error={}",
                    jobId, step, modelId, loggingError.getMessage());
            log.debug("[LLM] Call logging failure details for jobId={} step={} model={}", jobId, step, modelId, loggingError);
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

    private static String sha256prefix(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            log.warn("[LLM] SHA-256 not available for prefix generation");
            return "ERROR";
        }
    }

    private String serializeStructuredResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("[LLM] Structured response serialization failed, falling back to String.valueOf(): {}", e.getMessage());
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
