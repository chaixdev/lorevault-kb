package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service responsible for converting AI-identified anchors into precise character coordinates.
 * Implements Stage 2 of the scene detection pipeline: Coordinate Localization.
 */
@Component
@Slf4j
public class SceneCoordinateLocalizer {
    
    /**
     * Converts AI scene detection results with text anchors into precise character coordinates.
     * Since the new format only provides start anchors, scene boundaries are calculated by:
     * - Scene start: position of the start anchor
     * - Scene end: position of the next scene's start anchor (or end of chapter for last scene)
     * 
     * @param chapterText The full chapter text to search within
     * @param aiResults Scene detection results with start anchors
     * @return List of scenes with calculated character coordinates, sorted by position
     */
    public List<SceneWithCoordinates> localizeCoordinates(String chapterText, List<SceneDetectionResult> aiResults) {
        List<SceneWithCoordinates> coordinatedScenes = new ArrayList<>();
        
        log.debug("Localizing coordinates for {} scene results in text of length {}", 
                 aiResults.size(), chapterText.length());
        
        // First, sort the AI results by scene index to ensure proper ordering
        List<SceneDetectionResult> sortedResults = aiResults.stream()
            .sorted(Comparator.comparingInt(SceneDetectionResult::sceneIndex))
            .toList();
        
        for (int i = 0; i < sortedResults.size(); i++) {
            SceneDetectionResult result = sortedResults.get(i);
            try {
                log.debug("Processing scene {}: startAnchor='{}'", 
                         result.sceneIndex(), 
                         result.startAnchor().length() > 20 ? result.startAnchor().substring(0, 20) + "..." : result.startAnchor());
                
                // Find the start position for this scene
                long startPos = findAnchorPosition(chapterText, result.startAnchor(), true);
                
                if (startPos == -1) {
                    log.warn("Skipping scene {} because start anchor '{}' was not found", 
                            result.sceneIndex(), result.startAnchor());
                    continue;
                }
                
                // Determine the end position
                long endPos;
                if (i < sortedResults.size() - 1) {
                    // Not the last scene: try to find the start of the next scene
                    SceneDetectionResult nextResult = sortedResults.get(i + 1);
                    endPos = findAnchorPosition(chapterText, nextResult.startAnchor(), true);
                    if (endPos == -1) {
                        // Next scene anchor not found, extend this scene to end of chapter
                        log.warn("Next scene anchor '{}' not found, extending scene {} to end of chapter", 
                                nextResult.startAnchor(), result.sceneIndex());
                        endPos = chapterText.length();
                    }
                } else {
                    // Last scene: extend to the end of the chapter
                    endPos = chapterText.length();
                }
                
                if (startPos < endPos) {
                    coordinatedScenes.add(new SceneWithCoordinates(
                        result.sceneIndex(),
                        startPos,
                        endPos,
                        result.contextSummary()
                    ));
                    log.debug("Localized scene {}: start={}, end={}, length={}", 
                             result.sceneIndex(), startPos, endPos, endPos - startPos);
                } else {
                    log.warn("Failed to localize scene {}: invalid bounds startPos={}, endPos={}", 
                            result.sceneIndex(), startPos, endPos);
                }
            } catch (Exception e) {
                log.error("Error localizing scene {}: {}", result.sceneIndex(), e.getMessage());
            }
        }
        
        // Sort by start position to ensure proper ordering
        coordinatedScenes.sort(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset));
        
        log.debug("Successfully localized {} out of {} scenes", coordinatedScenes.size(), aiResults.size());
        
        return coordinatedScenes;
    }

    /**
     * Find the position of an anchor in the chapter text.
     * 
     * @param chapterText The full chapter text
     * @param anchor The anchor to find
     * @param isStart Whether this is a start anchor (find first occurrence) or end anchor (find last occurrence)
     * @return The character position, or -1 if not found
     */
    private long findAnchorPosition(String chapterText, String anchor, boolean isStart) {
        if (anchor == null || anchor.trim().isEmpty()) {
            log.debug("Empty anchor provided for {} position", isStart ? "start" : "end");
            return -1;
        }
        
        String normalizedAnchor = anchor.trim();
        
        log.debug("Searching for {} anchor: [{}] in text of length {}", 
                 isStart ? "start" : "end", normalizedAnchor, chapterText.length());
        
        int pos;
        if (isStart) {
            pos = chapterText.indexOf(normalizedAnchor);
            log.debug("Start anchor '{}' position: {}", 
                     normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor, 
                     pos);
        } else {
            pos = chapterText.lastIndexOf(normalizedAnchor);
            if (pos != -1) {
                pos = pos + normalizedAnchor.length();
            }
            log.debug("End anchor '{}' position: {}", 
                     normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor, 
                     pos);
        }
        
        return pos;
    }
}
