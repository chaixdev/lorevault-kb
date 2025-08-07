package com.lorevault.api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.model.Chapter;
import com.lorevault.api.model.Scene;
import com.lorevault.api.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for AI-powered scene detection within chapters.
 * Uses Gemini AI to identify semantic scene boundaries based on narrative shifts
 * (time, location, character focus) and creates Scene entities within the Chapter aggregate.
 * 
 * This service implements the v0.3.0 feature that transitions from deterministic
 * text chunking to AI-guided semantic segmentation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SceneDetectionService {

    private final ChatClient chatClient;
    private final ChapterRepository chapterRepository;
    private final ObjectMapper objectMapper;
    private final PromptLoaderService promptLoaderService;

    /**
     * Detects scenes within a chapter using AI analysis and updates the Chapter aggregate.
     * Implements the two-pass approach: AI identifies scenes with snippets, 
     * then code calculates exact character positions.
     * <p>
     * This method is NOT transactional because it includes external API calls.
     * Database operations are handled in a separate transactional method.
     * 
     * @param chapterId The UUID of the chapter to analyze
     * @return List of created Scene entities
     * @throws IllegalArgumentException if chapter not found
     */
    public List<Scene> detectScenesForChapter(UUID chapterId) {
        log.info("Starting scene detection for chapter: {}", chapterId);
        
        // Load the chapter (read-only operation)
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        
        // Check if scenes already exist
        if (!chapter.getScenes().isEmpty()) {
            log.info("Chapter {} already has {} scenes, skipping detection", 
                    chapterId, chapter.getScenes().size());
            return chapter.getScenes();
        }
        
        String chapterText = chapter.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("Chapter {} has no text content, cannot detect scenes", chapterId);
            return List.of();
        }
        
        try {
            // Stage 1: AI Scene Identification (external API call - no transaction)
            log.debug("Stage 1: Calling Gemini for scene detection on {} characters", chapterText.length());
            List<SceneDetectionResult> aiResults = callGeminiForSceneDetection(chapterText);
            
            if (aiResults.isEmpty()) {
                log.warn("No scenes detected for chapter {}", chapterId);
                return List.of();
            }
            
            // Stage 2: Coordinate Localization (pure computation - no transaction)
            log.debug("Stage 2: Localizing coordinates for {} detected scenes", aiResults.size());
            List<SceneWithCoordinates> scenesWithCoords = localizeSceneCoordinates(chapterText, aiResults);
            
            // Stage 3: Database Persistence (uses repository's implicit transaction)
            List<Scene> createdScenes = persistDetectedScenes(chapterId, scenesWithCoords);
            
            log.info("Successfully detected and created {} scenes for chapter {}", 
                    createdScenes.size(), chapterId);
            
            return createdScenes;
            
        } catch (Exception e) {
            log.error("Failed to detect scenes for chapter {}: {}", chapterId, e.getMessage(), e);
            throw new RuntimeException("Scene detection failed for chapter " + chapterId, e);
        }
    }

    /**
     * Persists the detected scenes to the database.
     * Uses repository's implicit transaction - no @Transactional needed for single save operation.
     * 
     * @param chapterId The UUID of the chapter
     * @param scenesWithCoords The detected scenes with coordinates
     * @return List of created Scene entities
     */
    public List<Scene> persistDetectedScenes(UUID chapterId, List<SceneWithCoordinates> scenesWithCoords) {
        log.debug("Starting persistence of {} scenes for chapter {}", 
                 scenesWithCoords.size(), chapterId);
        
        // Re-load the chapter
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        
        // Double-check that scenes haven't been created by another process
        if (!chapter.getScenes().isEmpty()) {
            log.info("Chapter {} already has {} scenes, returning existing scenes", 
                    chapterId, chapter.getScenes().size());
            return chapter.getScenes();
        }
        
        // Create scenes through Chapter aggregate
        List<Scene> createdScenes = createScenesFromCoordinates(chapter, scenesWithCoords);
        
        // Save the chapter (which will cascade to scenes) - repository method is implicitly transactional
        chapterRepository.save(chapter);
        
        log.debug("Successfully persisted {} scenes for chapter {}", 
                 createdScenes.size(), chapterId);
        
        return createdScenes;
    }

    /**
     * Calls Gemini AI to analyze the chapter text and detect scene boundaries.
     */
    private List<SceneDetectionResult> callGeminiForSceneDetection(String chapterText) {
        // Load the prompt template from resources
        String promptTemplate = promptLoaderService.getSceneDetectionPrompt();
        
        PromptTemplate template = new PromptTemplate(promptTemplate);
        Prompt prompt = template.create(Map.of("chapterText", chapterText));
        
        // Call Gemini AI
        String response = chatClient.prompt(prompt).call().content();
        
        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("Empty response from Gemini AI");
        }
        
        log.debug("Received scene detection response from Gemini: {}", 
                 response.length() > 500 ? response.substring(0, 500) + "..." : response);
        
        // Parse the JSON response
        return parseSceneDetectionResponse(response, chapterText.length());
    }
    
    /**
     * Parses the JSON response from Gemini into SceneDetectionResult objects.
     */
    private List<SceneDetectionResult> parseSceneDetectionResponse(String jsonResponse, int chapterTextLength) {
        try {
            // Clean up the response - sometimes AI includes markdown formatting
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();
            
            log.debug("Parsing JSON response: {}", cleanJson);
            
            SceneDetectionResponse response = objectMapper.readValue(cleanJson, SceneDetectionResponse.class);
            List<SceneDetectionResult> results = response.getScenes();
            
            log.info("Successfully parsed {} scene detection results", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Failed to parse scene detection JSON response: {}", e.getMessage());
            log.debug("Raw response was: {}", jsonResponse);
            
            // Fallback: return empty list to be handled by caller
            log.warn("Parsing failed, returning empty results for manual handling");
            return List.of();
        }
    }
    
    /**
     * Data class representing the result of AI scene detection.
     * Maps to the JSON structure returned by Gemini AI.
     */
    private record SceneDetectionResult(
        @JsonProperty("scene_index") int sceneIndex,
        @JsonProperty("start_snippet") String startSnippet,
        @JsonProperty("end_snippet") String endSnippet,
        @JsonProperty("context_summary") String contextSummary
    ) {}

    /**
     * Record to hold scenes with calculated character coordinates
     */
    public record SceneWithCoordinates(
            int sceneIndex,
            long startCharacterOffset,
            long endCharacterOffset,
            String contextSummary
    ) {}

    /**
     * Response structure for AI scene detection parsing
     */
    private static class SceneDetectionResponse {
        @JsonProperty("scenes")
        private List<SceneDetectionResult> scenes;
        
        public List<SceneDetectionResult> getScenes() {
            return scenes != null ? scenes : List.of();
        }
        
        public void setScenes(List<SceneDetectionResult> scenes) {
            this.scenes = scenes;
        }
    }

    /**
     * Stage 2: Localize scene coordinates from AI-identified snippets
     */
    private List<SceneWithCoordinates> localizeSceneCoordinates(String chapterText, List<SceneDetectionResult> aiResults) {
        List<SceneWithCoordinates> coordinatedScenes = new ArrayList<>();
        
        for (SceneDetectionResult result : aiResults) {
            try {
                long startPos = findSnippetPosition(chapterText, result.startSnippet(), true);
                long endPos = findSnippetPosition(chapterText, result.endSnippet(), false);
                
                if (startPos != -1 && endPos != -1 && startPos < endPos) {
                    coordinatedScenes.add(new SceneWithCoordinates(
                        result.sceneIndex(),
                        startPos,
                        endPos,
                        result.contextSummary()
                    ));
                    log.debug("Localized scene {}: start={}, end={}, length={}", 
                             result.sceneIndex(), startPos, endPos, endPos - startPos);
                } else {
                    log.warn("Failed to localize scene {}: startPos={}, endPos={}", 
                            result.sceneIndex(), startPos, endPos);
                }
            } catch (Exception e) {
                log.error("Error localizing scene {}: {}", result.sceneIndex(), e.getMessage());
            }
        }
        
        // Sort by start position to ensure proper ordering
        coordinatedScenes.sort(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset));
        
        return coordinatedScenes;
    }

    /**
     * Find the position of a snippet in the chapter text
     * @param chapterText The full chapter text
     * @param snippet The snippet to find
     * @param isStart Whether this is a start snippet (find first occurrence) or end snippet (find last occurrence)
     * @return The character position, or -1 if not found
     */
    private long findSnippetPosition(String chapterText, String snippet, boolean isStart) {
        if (snippet == null || snippet.trim().isEmpty()) {
            return -1;
        }
        
        String normalizedSnippet = snippet.trim();
        String normalizedText = chapterText;
        
        if (isStart) {
            int pos = normalizedText.indexOf(normalizedSnippet);
            return pos != -1 ? pos : -1;
        } else {
            int pos = normalizedText.lastIndexOf(normalizedSnippet);
            return pos != -1 ? pos + normalizedSnippet.length() : -1;
        }
    }

    /**
     * Create Scene entities from coordinated results through Chapter aggregate
     */
    private List<Scene> createScenesFromCoordinates(Chapter chapter, List<SceneWithCoordinates> coordinatedScenes) {
        List<Scene> createdScenes = new ArrayList<>();
        
        for (SceneWithCoordinates sceneCoords : coordinatedScenes) {
            if (isValidSceneCoordinates(sceneCoords, chapter.getRawText().length())) {
                Scene scene = chapter.addScene(
                        sceneCoords.sceneIndex(),
                        sceneCoords.startCharacterOffset(),
                        sceneCoords.endCharacterOffset(),
                        sceneCoords.contextSummary()
                );
                
                createdScenes.add(scene);
                
                log.debug("Created scene {} with coordinates: [{}-{}] length: {}", 
                         sceneCoords.sceneIndex(),
                         sceneCoords.startCharacterOffset(),
                         sceneCoords.endCharacterOffset(),
                         sceneCoords.endCharacterOffset() - sceneCoords.startCharacterOffset());
            } else {
                log.warn("Skipping invalid scene coordinates: scene={}, start={}, end={}, chapterLength={}", 
                        sceneCoords.sceneIndex(),
                        sceneCoords.startCharacterOffset(),
                        sceneCoords.endCharacterOffset(),
                        chapter.getRawText().length());
            }
        }
        
        return createdScenes;
    }

    /**
     * Validate scene coordinates
     */
    private boolean isValidSceneCoordinates(SceneWithCoordinates coords, int textLength) {
        return coords.startCharacterOffset() >= 0
            && coords.endCharacterOffset() <= textLength
            && coords.startCharacterOffset() < coords.endCharacterOffset()
            && (coords.endCharacterOffset() - coords.startCharacterOffset()) >= 100; // Minimum scene length
    }
}
