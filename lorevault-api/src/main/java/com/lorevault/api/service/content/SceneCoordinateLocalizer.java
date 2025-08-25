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
    
    // Constants for fuzzy matching
    private static final int MIN_WORDS_BEFORE_FUZZY = 5;
    private static final int MAX_LEVENSHTEIN_DISTANCE = 3; // Allow up to 3 character differences
    private static final double FUZZY_SIMILARITY_THRESHOLD = 0.85; // 85% similarity required
    
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
                
                // Calculate bounds for this scene
                long afterPosition = (i > 0 && !coordinatedScenes.isEmpty()) ? 
                    coordinatedScenes.get(coordinatedScenes.size() - 1).endCharacterOffset() : -1;
                
                long beforePosition = findNextAnchorBound(chapterText, sortedResults, i + 1, afterPosition);
                
                // Find the start position for this scene using enhanced matching
                long startPos = findAnchorPositionWithFallbacks(chapterText, result.startAnchor(), true, afterPosition, beforePosition);
                
                if (startPos == -1) {
                    log.warn("Skipping scene {} because start anchor '{}' was not found", 
                            result.sceneIndex(), result.startAnchor());
                    continue;
                }
                
                // Determine the end position using the same bound we calculated for the start search
                long endPos;
                if (beforePosition != -1) {
                    // Use the next anchor position we already found
                    endPos = beforePosition;
                    log.debug("Scene {} end position set to next anchor at: {}", result.sceneIndex(), endPos);
                } else {
                    // No next anchor found anywhere ahead, extend to end of chapter
                    endPos = chapterText.length();
                    log.debug("Scene {} extended to end of chapter (no subsequent anchors found)", result.sceneIndex());
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
     * Look ahead to find the next available anchor bound, searching through multiple scenes if necessary.
     * This handles the case where the immediate next scene's anchor might not be found,
     * but a subsequent scene's anchor can provide a useful boundary.
     * 
     * @param chapterText The full chapter text
     * @param sortedResults All scene results in order
     * @param startIndex Index to start searching from (typically current scene + 1)
     * @param afterPosition Search only after this position
     * @return Position of next found anchor, or -1 if none found
     */
    private long findNextAnchorBound(String chapterText, List<SceneDetectionResult> sortedResults, int startIndex, long afterPosition) {
        // Look ahead through remaining scenes to find the next usable anchor
        for (int j = startIndex; j < sortedResults.size(); j++) {
            SceneDetectionResult futureResult = sortedResults.get(j);
            long nextPos = findAnchorPositionWithFallbacks(chapterText, futureResult.startAnchor(), true, afterPosition, -1);
            if (nextPos != -1) {
                log.debug("Found next boundary anchor for scene {} at position {} (from scene {})", 
                         startIndex - 1, nextPos, j);
                return nextPos;
            } else {
                log.debug("Scene {} anchor not found, looking further ahead...", j);
            }
        }
        
        log.debug("No subsequent anchor found for boundary - will extend to chapter end");
        return -1; // No subsequent anchor found
    }

    /**
     * Enhanced anchor finding with multi-tier fallback strategy to handle LLM drift.
     * Tries: exact match -> word trimming -> fuzzy matching -> fail
     * 
     * @param chapterText The full chapter text
     * @param anchor The anchor to find
     * @param isStart Whether this is a start anchor (find first occurrence) or end anchor (find last occurrence)
     * @param afterPosition Only search after this position (for sequence validation), or -1 to search entire text
     * @param beforePosition Only search before this position (for bounded validation), or -1 to search to end
     * @return The character position, or -1 if not found
     */
    private long findAnchorPositionWithFallbacks(String chapterText, String anchor, boolean isStart, long afterPosition, long beforePosition) {
        if (anchor == null || anchor.trim().isEmpty()) {
            log.debug("Empty anchor provided for {} position", isStart ? "start" : "end");
            return -1;
        }
        
        String normalizedAnchor = anchor.trim();
        
        log.debug("Searching for {} anchor: [{}] in text of length {} (after: {}, before: {})", 
                 isStart ? "start" : "end", normalizedAnchor, chapterText.length(), afterPosition, beforePosition);
        
        // Tier 1: Exact match (existing behavior)
        long position = findExactMatch(chapterText, normalizedAnchor, isStart, afterPosition, beforePosition);
        if (position != -1) {
            log.debug("Found exact match for anchor '{}' at position {}", 
                     normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor, 
                     position);
            return position;
        }
        
        // Tier 2: Word trimming with uniqueness and bounds validation
        position = findWithWordTrimming(chapterText, normalizedAnchor, isStart, afterPosition, beforePosition);
        if (position != -1) {
            log.info("Found anchor using word trimming for '{}' at position {}", 
                    normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor, 
                    position);
            return position;
        }
        
        // Tier 3: Fuzzy matching
        position = findWithFuzzyMatching(chapterText, normalizedAnchor, isStart, afterPosition, beforePosition);
        if (position != -1) {
            log.info("Found anchor using fuzzy matching for '{}' at position {}", 
                    normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor, 
                    position);
            return position;
        }
        
        log.warn("All fallback methods failed for anchor: '{}'", 
                normalizedAnchor.length() > 40 ? normalizedAnchor.substring(0, 40) + "..." : normalizedAnchor);
        return -1;
    }
    
    /**
     * Tier 1: Exact string matching (original behavior) with bounds checking
     */
    private long findExactMatch(String chapterText, String anchor, boolean isStart, long afterPosition, long beforePosition) {
        int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
        int searchEnd = (beforePosition == -1) ? chapterText.length() : (int) beforePosition;
        
        if (searchStart >= searchEnd) {
            return -1; // Invalid search bounds
        }
        
        String searchArea = chapterText.substring(searchStart, searchEnd);
        
        // Try exact match first
        int pos = isStart ? searchArea.indexOf(anchor) : searchArea.lastIndexOf(anchor);
        
        if (pos == -1) {
            // Try with normalized whitespace for comparison
            String normalizedAnchor = normalizeWhitespaceForComparison(anchor);
            String normalizedSearchArea = normalizeWhitespaceForComparison(searchArea);
            
            int normalizedPos = isStart ? normalizedSearchArea.indexOf(normalizedAnchor) : normalizedSearchArea.lastIndexOf(normalizedAnchor);
            
            if (normalizedPos != -1) {
                // Find the actual position in the original text by mapping back from normalized position
                pos = mapNormalizedPositionToOriginal(searchArea, normalizedSearchArea, normalizedPos);
                log.debug("Found match using whitespace normalization at position {}", pos);
            }
        }
        
        if (pos == -1) {
            return -1;
        }
        
        int actualPos = searchStart + pos;
        return isStart ? actualPos : actualPos + anchor.length();
    }
    
    /**
     * Tier 2: Progressive word trimming while maintaining uniqueness and bounds
     */
    private long findWithWordTrimming(String chapterText, String anchor, boolean isStart, long afterPosition, long beforePosition) {
        String[] words = anchor.split("\\s+");
        
        // Need at least MIN_WORDS_BEFORE_FUZZY words to start trimming
        if (words.length < MIN_WORDS_BEFORE_FUZZY) {
            log.debug("Anchor too short for word trimming: {} words", words.length);
            return -1;
        }
        
        int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
        int searchEnd = (beforePosition == -1) ? chapterText.length() : (int) beforePosition;
        
        if (searchStart >= searchEnd) {
            log.debug("Invalid search bounds for word trimming: start={}, end={}", searchStart, searchEnd);
            return -1;
        }
        
        String searchArea = chapterText.substring(searchStart, searchEnd);
        
        // Try progressively shorter versions by removing words from the end
        for (int wordCount = words.length - 1; wordCount >= MIN_WORDS_BEFORE_FUZZY; wordCount--) {
            String trimmedAnchor = String.join(" ", java.util.Arrays.copyOf(words, wordCount));
            
            // Try exact match first, then normalized match
            List<Integer> matches = findAllMatchesInBounds(searchArea, trimmedAnchor, 0, searchArea.length());
            
            if (matches.isEmpty()) {
                // Try with whitespace normalization
                String normalizedAnchor = normalizeWhitespaceForComparison(trimmedAnchor);
                String normalizedSearchArea = normalizeWhitespaceForComparison(searchArea);
                matches = findAllNormalizedMatchesInBounds(searchArea, normalizedSearchArea, normalizedAnchor, 0, searchArea.length());
            }
            
            if (matches.size() == 1) {
                // Unique match found within bounds
                int pos = matches.get(0);
                log.debug("Found unique bounded match using {} words: '{}' at position {}", 
                         wordCount, trimmedAnchor.length() > 30 ? trimmedAnchor.substring(0, 30) + "..." : trimmedAnchor, pos);
                int actualPos = searchStart + pos;
                return isStart ? actualPos : actualPos + trimmedAnchor.length();
            } else if (matches.isEmpty()) {
                log.debug("No bounded match found for trimmed anchor: '{}'", trimmedAnchor);
                continue; // Try shorter version
            } else {
                log.debug("Multiple bounded matches found for trimmed anchor '{}': {} occurrences within bounds", 
                         trimmedAnchor, matches.size());
                // Multiple matches - not unique enough within bounds, try shorter version
                continue;
            }
        }
        
        log.debug("Word trimming failed - no unique bounded match found");
        return -1;
    }
    
    /**
     * Tier 3: Fuzzy matching using Levenshtein distance with bounds checking
     */
    private long findWithFuzzyMatching(String chapterText, String anchor, boolean isStart, long afterPosition, long beforePosition) {
        int anchorLength = anchor.length();
        int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
        int searchEnd = (beforePosition == -1) ? chapterText.length() - anchorLength : (int) beforePosition - anchorLength;
        
        if (searchStart >= searchEnd) {
            return -1; // Invalid search bounds
        }
        
        // Search for the best fuzzy match within bounds
        int bestPosition = -1;
        int bestDistance = Integer.MAX_VALUE;
        
        // Use a sliding window approach to find the best match
        for (int i = searchStart; i <= searchEnd; i++) {
            String candidate = chapterText.substring(i, i + anchorLength);
            int distance = levenshteinDistance(anchor, candidate);
            
            // Check if this is a good enough match
            if (distance <= MAX_LEVENSHTEIN_DISTANCE && distance < bestDistance) {
                double similarity = 1.0 - (double) distance / Math.max(anchor.length(), candidate.length());
                if (similarity >= FUZZY_SIMILARITY_THRESHOLD) {
                    bestDistance = distance;
                    bestPosition = i;
                }
            }
        }
        
        if (bestPosition != -1) {
            log.debug("Found bounded fuzzy match with distance {} at position {}", bestDistance, bestPosition);
            return isStart ? bestPosition : bestPosition + anchorLength;
        }
        
        log.debug("No suitable bounded fuzzy match found within threshold");
        return -1;
    }
    
    /**
     * Find all occurrences of a substring in a bounded area of the text
     */
    private List<Integer> findAllMatchesInBounds(String text, String substring, int searchStart, int searchEnd) {
        List<Integer> matches = new ArrayList<>();
        
        if (searchStart >= searchEnd || searchStart < 0 || searchEnd > text.length()) {
            return matches; // Invalid bounds
        }
        
        String searchArea = text.substring(searchStart, searchEnd);
        int index = searchArea.indexOf(substring);
        
        while (index != -1 && searchStart + index + substring.length() <= searchEnd) {
            matches.add(searchStart + index);
            index = searchArea.indexOf(substring, index + 1);
        }
        
        return matches;
    }
    
    /**
     * Find all normalized matches in a bounded area, mapping positions back to original text
     */
    private List<Integer> findAllNormalizedMatchesInBounds(String originalText, String normalizedText, String normalizedSubstring, int searchStart, int searchEnd) {
        List<Integer> matches = new ArrayList<>();
        
        int index = normalizedText.indexOf(normalizedSubstring);
        
        while (index != -1) {
            // Map the normalized position back to original text
            int originalPos = mapNormalizedPositionToOriginal(originalText, normalizedText, index);
            
            if (originalPos >= searchStart && originalPos < searchEnd) {
                matches.add(originalPos);
            }
            
            index = normalizedText.indexOf(normalizedSubstring, index + 1);
        }
        
        return matches;
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     * (minimum number of single-character edits needed to transform one string into the other)
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        
        // Create a matrix to store the distances
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        // Initialize the matrix
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }
        
        // Fill the matrix
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j] + 1,      // deletion
                        Math.min(
                            dp[i][j - 1] + 1,  // insertion
                            dp[i - 1][j - 1] + 1 // substitution
                        )
                    );
                }
            }
        }
        
        return dp[len1][len2];
    }
    
    /**
     * Normalize whitespace for comparison purposes only.
     * Collapses multiple whitespace characters (spaces, tabs, newlines) into single spaces
     * and trims leading/trailing whitespace.
     */
    private String normalizeWhitespaceForComparison(String text) {
        if (text == null) return null;
        
        // Replace all whitespace sequences with single spaces and trim
        return text.replaceAll("\\s+", " ").trim();
    }
    
    /**
     * Map a position found in normalized text back to the original text.
     * This preserves the original formatting while allowing flexible matching.
     */
    private int mapNormalizedPositionToOriginal(String originalText, String normalizedText, int normalizedPos) {
        if (normalizedPos == 0) {
            // Find the first non-whitespace character in original
            for (int i = 0; i < originalText.length(); i++) {
                if (!Character.isWhitespace(originalText.charAt(i))) {
                    return i;
                }
            }
            return 0;
        }
        
        // Walk through both texts simultaneously to map the position
        int originalPos = 0;
        int normalizedCount = 0;
        boolean inWhitespace = false;
        
        for (int i = 0; i < originalText.length() && normalizedCount < normalizedPos; i++) {
            char c = originalText.charAt(i);
            
            if (Character.isWhitespace(c)) {
                if (!inWhitespace) {
                    // First whitespace character maps to a space in normalized text
                    normalizedCount++;
                    inWhitespace = true;
                }
                // Subsequent whitespace characters don't increment normalizedCount
            } else {
                // Non-whitespace character
                normalizedCount++;
                inWhitespace = false;
            }
            
            originalPos = i;
            
            if (normalizedCount >= normalizedPos) {
                // We've reached the target position
                return inWhitespace ? i : i + 1;
            }
        }
        
        return originalPos;
    }
}
