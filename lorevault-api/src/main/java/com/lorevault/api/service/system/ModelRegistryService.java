package com.lorevault.api.service.system;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for managing AI model registry and mapping model IDs to human-readable names.
 * This service centralizes model configuration to avoid hardcoded model references throughout the codebase.
 * Renamed from LlmModelInfoService to support broader model types (LLM, embedding, etc.).
 */
@Service
@Slf4j
public class ModelRegistryService {

    @Getter
    @Value("${lorevault.ai.models.nlp-big.model:unknown}")
    private String currentModelId;

    // Static mapping of model IDs to human-readable names
    private static final Map<String, String> MODEL_NAME_MAPPING = Map.of(
        "gemma-3-4b-it", "Gemma 3 4B Instruct",
        "gemma-2-9b-it", "Gemma 2 9B Instruct", 
        "gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite",
        "gemini-1.5-flash", "Gemini 1.5 Flash",
        "gemini-1.5-pro", "Gemini 1.5 Pro",
        "gpt-4o-mini", "GPT-4o Mini",
        "gpt-4o", "GPT-4o",
        "claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"
    );

    /**
     * Gets the human-readable name for the currently configured model.
     * 
     * @return The display name of the current model
     */
    public String getCurrentModelDisplayName() {
        String displayName = MODEL_NAME_MAPPING.get(currentModelId);
        if (displayName == null) {
            log.warn("Unknown model ID '{}', using ID as display name", currentModelId);
            return currentModelId;
        }
        return displayName;
    }

    /**
     * Gets the human-readable name for any model ID.
     * 
     * @param modelId The model identifier
     * @return The display name, or the model ID if no mapping exists
     */
    public String getModelDisplayName(String modelId) {
        return MODEL_NAME_MAPPING.getOrDefault(modelId, modelId);
    }

    /**
     * Checks if the given model ID is supported/known.
     * 
     * @param modelId The model identifier to check
     * @return true if the model is in our mapping, false otherwise
     */
    public boolean isModelSupported(String modelId) {
        return MODEL_NAME_MAPPING.containsKey(modelId);
    }

    /**
     * Gets all supported model mappings.
     * 
     * @return Map of model IDs to display names
     */
    public Map<String, String> getAllSupportedModels() {
        return Map.copyOf(MODEL_NAME_MAPPING);
    }
}
