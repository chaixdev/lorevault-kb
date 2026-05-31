package com.lorevault.api.config;

import com.lorevault.api.ai.ModelSlot;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for AI prompts under lorevault.ai.prompts.
 * Maps to the prompt configuration structure in application.yml.
 */
@ConfigurationProperties(prefix = "lorevault.ai.prompts")
@Validated
public record LoreVaultPromptProperties(
    String basePath,
    PromptConfig sceneDetection,
    PromptConfig chapterSegmentation,
    PromptConfig sceneAnalysis,
    PromptConfig entityExtraction,
    PromptConfig ragAnswerGeneration
) {
    
    public LoreVaultPromptProperties {
        // Apply defaults
        if (basePath == null) {
            basePath = "classpath:prompts";
        }
    }
    
    /**
     * Configuration for a specific prompt task.
     */
    public record PromptConfig(
        String systemPrompt,
        String model
    ) {}
    
    /**
     * Get the full resource path for a prompt file.
     */
    public String getPromptPath(String promptFile) {
        return basePath + "/" + promptFile;
    }
    
    /**
     * Get chapter segmentation prompt path.
     */
    public String getChapterSegmentationPath() {
        return chapterSegmentation != null && chapterSegmentation.systemPrompt() != null 
            ? getPromptPath(chapterSegmentation.systemPrompt()) 
            : getPromptPath("chapter-segmentation.txt");
    }
    
    /**
     * Get scene analysis prompt path.
     */
    public String getSceneAnalysisPath() {
        return sceneAnalysis != null && sceneAnalysis.systemPrompt() != null 
            ? getPromptPath(sceneAnalysis.systemPrompt()) 
            : getPromptPath("scene-analysis.txt");
    }

    /**
     * Get event coreference system prompt path.
     */
    public String getEventCorefSystemPath() {
        return getPromptPath("event-coref-system.st");
    }

    /**
     * Get event merge verification system prompt path.
     */
    public String getEventMergeSystemPath() {
        return getPromptPath("event-merge-system.st");
    }
    
    /**
     * Get chapter segmentation model.
     */
    public String getChapterSegmentationModel() {
        return chapterSegmentation != null && chapterSegmentation.model() != null 
            ? chapterSegmentation.model() 
            : ModelSlot.NLP_SMALL.slotName();
    }
    
    /**
     * Get scene analysis model.
     */
    public String getSceneAnalysisModel() {
        return sceneAnalysis != null && sceneAnalysis.model() != null 
            ? sceneAnalysis.model() 
            : ModelSlot.NLP_SMALL.slotName();
    }
    
    /**
     * Get RAG answer generation prompt path.
     */
    public String getRagAnswerGenerationPath() {
        return ragAnswerGeneration != null && ragAnswerGeneration.systemPrompt() != null 
            ? getPromptPath(ragAnswerGeneration.systemPrompt()) 
            : getPromptPath("rag-answer-generation.txt");
    }
    
    /**
     * Get RAG answer generation model.
     */
    public String getRagAnswerGenerationModel() {
        return ragAnswerGeneration != null && ragAnswerGeneration.model() != null 
            ? ragAnswerGeneration.model() 
            : ModelSlot.NLP_BIG.slotName();
    }
}
