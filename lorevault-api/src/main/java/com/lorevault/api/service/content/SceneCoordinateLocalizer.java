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
     * 
     * @param chapterText The full chapter text to search within
     * @param aiResults Scene detection results with start/end anchors
     * @return List of scenes with calculated character coordinates, sorted by position
     */
    public List<SceneWithCoordinates> localizeCoordinates(String chapterText, List<SceneDetectionResult> aiResults) {
        List<SceneWithCoordinates> coordinatedScenes = new ArrayList<>();
        
        log.debug("Localizing coordinates for {} scene results in text of length {}", 
                 aiResults.size(), chapterText.length());
        
        for (SceneDetectionResult result : aiResults) {
            try {
                log.debug("Processing scene {}: startAnchor='{}', endAnchor='{}'", 
                         result.sceneIndex(), 
                         result.startAnchor().length() > 20 ? result.startAnchor().substring(0, 20) + "..." : result.startAnchor(),
                         result.endAnchor().length() > 20 ? result.endAnchor().substring(0, 20) + "..." : result.endAnchor());
                
                // Fix: When searching for start anchors, use the startAnchor field
                long startPos = findAnchorPosition(chapterText, result.startAnchor(), true);
                long endPos = findAnchorPosition(chapterText, result.endAnchor(), false);
                
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
