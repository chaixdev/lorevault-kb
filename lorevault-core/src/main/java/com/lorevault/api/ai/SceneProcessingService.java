package com.lorevault.api.ai;

import com.lorevault.api.content.Scene;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.ai.SceneDetectionResult;
import com.lorevault.api.ai.SceneWithCoordinates;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.ingestion.IngestionFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified service responsible for scene processing operations:
 * XML parsing, coordinate localization, and persistence.
 * 
 * Consolidates the functionality previously spread across:
 * - ScenePersistenceService (database persistence)
 * - SceneCoordinateLocalizer (coordinate calculation)
 * - SceneDetectionXmlParser (XML response parsing)
 * 
 * This service provides granular operations to support different usage patterns.
 * Note: AI scene detection is handled by SceneDetectionClient and related
 * orchestration services to avoid circular dependencies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SceneProcessingService {

    private final ChapterGraphRepository chapterRepo;
    private final SceneGraphRepository sceneRepo;

    // =============================================================================
    // HIGH-LEVEL WORKFLOW METHODS
    // =============================================================================

    /**
     * Retrieve all scenes for a chapter.
     * 
     * @param chapterId The chapter ID
     * @return List of scenes for the chapter
     */
    public List<Scene> getScenesByChapterId(UUID chapterId) {
        return sceneRepo.findByChapterId(chapterId);
    }

    /**
     * Delete all scenes for a chapter.
     * 
     * @param chapterId The chapter ID
     */
    @Transactional
    public void deleteScenesByChapterId(UUID chapterId) {
        log.debug("Deleting all scenes for chapter {}", chapterId);
        sceneRepo.deleteByChapterId(chapterId);
    }

    // =============================================================================
    // GRANULAR PROCESSING METHODS
    // =============================================================================

    /**
     * Persist detected scenes to the database.
     * Separated from detection to maintain proper transaction boundaries.
     * 
     * @param chapterId        The UUID of the chapter
     * @param scenesWithCoords The detected scenes with coordinates
     * @return List of persisted Scene entities
     */
    @Transactional
    public List<Scene> persistDetectedScenes(UUID chapterId, List<SceneWithCoordinates> scenesWithCoords) {
        log.debug("Persisting {} scenes for chapter {}", scenesWithCoords.size(), chapterId);

        if (scenesWithCoords.isEmpty()) {
            return List.of();
        }

        // Avoid duplicate persistence if scenes already exist
        if (!sceneRepo.findByChapterId(chapterId).isEmpty()) {
            log.info("Chapter {} already has scenes; returning existing", chapterId);
        return sceneRepo.findByChapterId(chapterId);
        }

        // Fetch chapter text to extract scene content
        String chapterText = chapterRepo.findById(chapterId)
                .map(c -> c.getRawText())
                .orElse(null);

        final String finalChapterText = chapterText;
        List<Scene> scenes = scenesWithCoords.stream().map(s -> {
            Scene scene = new Scene();
            scene.setSceneIndex(s.sceneIndex());
            scene.setStartCharacterOffset(s.startCharacterOffset());
            scene.setEndCharacterOffset(s.endCharacterOffset());
            scene.setContextSummary(s.contextSummary());
            scene.setChronology(s.chronology());
            scene.setChronologyCertainty(s.chronologyCertainty());
            scene.setChronologyMarker(s.chronologyMarker());

            LinkedHashSet<String> labels = new LinkedHashSet<>();
            labels.add(Scene.EVENT_LABEL);
            if (s.potentialSplitSceneStart()) {
                labels.add(Scene.POTENTIAL_SPLIT_SCENE_START_LABEL);
            }
            if (s.potentialSplitSceneEnd()) {
                labels.add(Scene.POTENTIAL_SPLIT_SCENE_END_LABEL);
            }
            scene.setLabels(new ArrayList<>(labels));

            // Extract and set the scene text
            if (finalChapterText != null) {
                try {
                    int start = (int) s.startCharacterOffset();
                    int end = (int) s.endCharacterOffset();
                    if (start >= 0 && end <= finalChapterText.length() && start < end) {
                        String sceneText = finalChapterText.substring(start, end);
                        scene.setText(sceneText);
                        log.trace("Extracted scene text for scene {}: {} chars", s.sceneIndex(), sceneText.length());
                    } else {
                        log.warn("Invalid scene coordinates for scene {}: start={}, end={}, chapterLen={}",
                                s.sceneIndex(), start, end, finalChapterText.length());
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract scene text for scene {}: {}", s.sceneIndex(), e.getMessage());
                }
            }

            return scene;
        }).collect(Collectors.toList());

        List<Scene> toSave = scenes.stream()
                .peek(s -> {
                    if (s.getId() == null) {
                        s.setId(UUID.randomUUID());
                    }
                    if (s.getChapterId() == null) {
                        s.setChapterId(chapterId);
                    }
                })
                .collect(Collectors.toList());
        List<Scene> savedScenes = sceneRepo.saveAll(toSave);
        for (Scene savedScene : savedScenes) {
            if (savedScene.getId() != null) {
                sceneRepo.linkSceneToChapter(chapterId, savedScene.getId());
            }
        }
        return savedScenes;
    }

    /**
     * Parse XML response from AI scene detection into SceneDetectionResult objects.
     * Handles markdown fencing, CDATA sections, and malformed XML gracefully.
     * 
     * @param xmlResponse       Raw XML response from AI model
     * @param chapterTextLength Length of the chapter text (for validation)
     * @return List of parsed scene detection results
     */
    public List<SceneDetectionResult> parseSceneDetectionXml(String xmlResponse, int chapterTextLength) {
        try {
            log.trace("Parsing XML response of length: {}", xmlResponse.length());

            String cleanXml = cleanupXmlResponse(xmlResponse);
            log.trace("Full cleaned XML response: {}", cleanXml);

            if (!isValidXmlStructure(cleanXml)) {
                return List.of();
            }

            Document document = parseXmlDocument(cleanXml);
            if (document == null) {
                return List.of();
            }

            List<SceneDetectionResult> results = extractSceneResults(document);

            log.info("Successfully parsed {} scene detection results", results.size());
            return results;

        } catch (Exception e) {
            log.error("Failed to parse scene detection XML response: {}", e.getMessage());
            log.debug("Raw response was: {}", xmlResponse);

            log.warn("Parsing failed, returning empty results for manual handling");
            return List.of();
        }
    }

    /**
     * Convert AI-identified anchors into precise character coordinates.
     * Implements sophisticated coordinate localization with fallback strategies.
     * 
     * @param chapterText The full chapter text to search within
     * @param aiResults   Scene detection results with start anchors
     * @return List of scenes with calculated character coordinates, sorted by
     *         position
     */
    public List<SceneWithCoordinates> localizeSceneCoordinates(String chapterText,
            List<SceneDetectionResult> aiResults) {
        List<SceneWithCoordinates> coordinatedScenes = new ArrayList<>();

        log.debug("Localizing coordinates for {} scene results in text of length {}",
                aiResults.size(), chapterText.length());

        List<SceneDetectionResult> sortedResults = aiResults.stream()
                .sorted(Comparator.comparingInt(SceneDetectionResult::sceneIndex))
                .toList();

        for (int i = 0; i < sortedResults.size(); i++) {
            SceneDetectionResult result = sortedResults.get(i);
            try {
                log.debug("Processing scene {}: startAnchor='{}'",
                        result.sceneIndex(),
                        result.startAnchor().length() > 20 ? result.startAnchor().substring(0, 20) + "..."
                                : result.startAnchor());

                long afterPosition = (i > 0 && !coordinatedScenes.isEmpty())
                        ? coordinatedScenes.get(coordinatedScenes.size() - 1).endCharacterOffset()
                        : -1;

                long beforePosition = findNextAnchorBound(chapterText, sortedResults, i + 1, afterPosition);

                long startPos = findAnchorPositionWithFallbacks(chapterText, result.startAnchor(), true, afterPosition,
                        beforePosition);

                if (startPos == -1) {
                    throw sceneAnchorMismatch(result.sceneIndex(), result.startAnchor());
                }

                long endPos;
                if (beforePosition != -1) {
                    endPos = beforePosition;
                    log.debug("Scene {} end position set to next anchor at: {}", result.sceneIndex(), endPos);
                } else {
                    endPos = chapterText.length();
                    log.debug("Scene {} extended to end of chapter (no subsequent anchors found)", result.sceneIndex());
                }

                if (startPos < endPos) {
                    coordinatedScenes.add(new SceneWithCoordinates(
                            result.sceneIndex(),
                            startPos,
                            endPos,
                            result.contextSummary(),
                            result.chronology(),
                            result.chronologyCertainty(),
                            result.chronologyMarker(),
                            false,
                            false));
                    log.debug("Localized scene {}: start={}, end={}, length={}",
                            result.sceneIndex(), startPos, endPos, endPos - startPos);
                } else {
                    throw sceneLocalizationFailure(
                            "SCENE_LOCALIZATION_INVALID_BOUNDS",
                            String.format(
                                    "Failed to localize scene %d: invalid bounds startPos=%d, endPos=%d",
                                    result.sceneIndex(),
                                    startPos,
                                    endPos
                            ),
                            result.sceneIndex(),
                            result.startAnchor(),
                            null
                    );
                }
            } catch (SceneLocalizationException e) {
                throw e;
            } catch (Exception e) {
                throw sceneLocalizationFailure(
                        "SCENE_LOCALIZATION_FAILED",
                        String.format(
                                "Error localizing scene %d: %s",
                                result.sceneIndex(),
                                e.getMessage()
                        ),
                        result.sceneIndex(),
                        result.startAnchor(),
                        e
                );
            }
        }

        coordinatedScenes.sort(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset));

        log.debug("Successfully localized {} out of {} scenes", coordinatedScenes.size(), aiResults.size());

        return coordinatedScenes;
    }

    private SceneLocalizationException sceneAnchorMismatch(int sceneIndex, String startAnchor) {
        return sceneLocalizationFailure(
                "SCENE_LOCALIZATION_ANCHOR_NOT_FOUND",
                String.format(
                        "Failed to localize scene %d because start anchor '%s' was not found",
                        sceneIndex,
                        startAnchor
                ),
                sceneIndex,
                startAnchor,
                null
        );
    }

    private SceneLocalizationException sceneLocalizationFailure(
            String code,
            String message,
            int sceneIndex,
            String startAnchor,
            Throwable cause
    ) {
        IngestionFailure failure = IngestionFailure.builder(code, message)
                .exceptionType(SceneLocalizationException.class.getSimpleName())
                .stage("SCENE_SEGMENTATION")
                .detail("sceneIndex", sceneIndex)
                .detail("startAnchor", anchorPreview(startAnchor))
                .build();
        return cause == null ? new SceneLocalizationException(failure) : new SceneLocalizationException(failure, cause);
    }

    private String anchorPreview(String anchor) {
        if (anchor == null) {
            return null;
        }
        return anchor.length() <= 160 ? anchor : anchor.substring(0, 157) + "...";
    }

    // =============================================================================
    // PRIVATE XML PARSING METHODS
    // =============================================================================

    private String cleanupXmlResponse(String xmlResponse) {
        String cleanXml = xmlResponse.trim();
        if (cleanXml.startsWith("```xml")) {
            cleanXml = cleanXml.substring(6);
        }
        if (cleanXml.startsWith("```")) {
            cleanXml = cleanXml.substring(3);
        }
        if (cleanXml.endsWith("```")) {
            cleanXml = cleanXml.substring(0, cleanXml.length() - 3);
        }
        return cleanXml.trim();
    }

    private boolean isValidXmlStructure(String cleanXml) {
        if (!cleanXml.trim().startsWith("<")) {
            log.error("XML does not start with '<' character. First 50 chars: '{}'",
                    cleanXml.substring(0, Math.min(50, cleanXml.length())));
            return false;
        }
        return true;
    }

    private Document parseXmlDocument(String cleanXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        log.debug("About to parse XML of length: {}", cleanXml.length());
        log.debug("XML starts with: '{}'", cleanXml.substring(0, Math.min(100, cleanXml.length())));

        byte[] xmlBytes = cleanXml.getBytes("UTF-8");
        log.debug("XML byte array length: {}, first 10 bytes: {}", xmlBytes.length,
                java.util.Arrays.toString(java.util.Arrays.copyOf(xmlBytes, Math.min(10, xmlBytes.length))));

        Document document = builder.parse(new ByteArrayInputStream(xmlBytes));
        log.debug("XML parsing completed successfully, document is not null: {}", document != null);

        if (document == null) {
            log.error("Document is null after parsing!");
            return null;
        }

        document.getDocumentElement().normalize();
        log.debug("Document normalized, getting root element...");

        Element rootElement = document.getDocumentElement();
        if (rootElement == null) {
            log.error("Root element is null!");
            return null;
        }

        log.debug("Root element name: '{}'", rootElement.getNodeName());
        return document;
    }

    private List<SceneDetectionResult> extractSceneResults(Document document) {
        List<SceneDetectionResult> results = new ArrayList<>();
        Element rootElement = document.getDocumentElement();

        NodeList sceneNodes = document.getElementsByTagName("scene");
        log.debug("Found {} scene nodes in document", sceneNodes.getLength());

        if (sceneNodes.getLength() == 0) {
            logMissingSceneNodes(rootElement);
            return results;
        }

        for (int i = 0; i < sceneNodes.getLength(); i++) {
            Node sceneNode = sceneNodes.item(i);

            if (sceneNode.getNodeType() == Node.ELEMENT_NODE) {
                Element sceneElement = (Element) sceneNode;
                int sceneIndex = getIntValue(sceneElement, "index");
                if (sceneIndex < 0) {
                    sceneIndex = i;
                }
                String startAnchor = getStringValue(sceneElement, "start_anchor");
                String contextSummary = getStringValue(sceneElement, "context_summary");
                String breakReason = getStringValue(sceneElement, "break_reason");
                String chronology = getStringValue(sceneElement, "chronology");
                String chronologyCertainty = getStringValue(sceneElement, "chronology_certainty");
                String chronologyMarker = getStringValue(sceneElement, "chronology_marker");

                if (startAnchor != null && contextSummary != null) {
                    results.add(new SceneDetectionResult(
                            sceneIndex,
                            startAnchor,
                            contextSummary,
                            breakReason,
                            chronology,
                            chronologyCertainty,
                            chronologyMarker));
                } else {
                    log.warn("Skipping incomplete scene: index={}, start={}, context={}, reason={}",
                            sceneIndex,
                            startAnchor != null ? "present" : "missing",
                            contextSummary != null ? "present" : "missing",
                            breakReason != null ? "present" : "missing");
                }
            }
        }

        return results;
    }

    private void logMissingSceneNodes(Element rootElement) {
        log.warn("No scene nodes found! Root element children:");
        NodeList rootChildren = rootElement.getChildNodes();
        for (int j = 0; j < rootChildren.getLength(); j++) {
            Node child = rootChildren.item(j);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                log.warn("  Child element: '{}'", child.getNodeName());
            }
        }
    }

    private int getIntValue(Element parentElement, String tagName) {
        NodeList nodeList = parentElement.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String textContent = nodeList.item(0).getTextContent().trim();
            try {
                return Integer.parseInt(textContent);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse integer value from element '{}': '{}'", tagName, textContent);
                return 0;
            }
        }
        return 0;
    }

    private String getStringValue(Element parentElement, String tagName) {
        NodeList nodeList = parentElement.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            String textContent = node.getTextContent();
            return textContent != null ? textContent.trim() : null;
        }
        return null;
    }

    // =============================================================================
    // PRIVATE COORDINATE LOCALIZATION METHODS
    // =============================================================================

    // Constants for coordinate localization
    private static final int MIN_WORDS_BEFORE_FUZZY = 5;
    private static final int MAX_LEVENSHTEIN_DISTANCE = 3;
    private static final double FUZZY_SIMILARITY_THRESHOLD = 0.85;

    private long findNextAnchorBound(String chapterText, List<SceneDetectionResult> sortedResults, int startIndex,
            long afterPosition) {
        for (int j = startIndex; j < sortedResults.size(); j++) {
            SceneDetectionResult futureResult = sortedResults.get(j);
            long nextPos = findAnchorPositionWithFallbacks(chapterText, futureResult.startAnchor(), true, afterPosition,
                    -1);
            if (nextPos != -1) {
                log.debug("Found next boundary anchor for scene {} at position {} (from scene {})",
                        startIndex - 1, nextPos, j);
                return nextPos;
            } else {
                log.debug("Scene {} anchor not found, looking further ahead...", j);
            }
        }

        log.debug("No subsequent anchor found for boundary - will extend to chapter end");
        return -1;
    }

    private long findAnchorPositionWithFallbacks(String chapterText, String anchor, boolean isStart, long afterPosition,
            long beforePosition) {
        if (anchor == null || anchor.trim().isEmpty()) {
            log.debug("Empty anchor provided for {} position", isStart ? "start" : "end");
            return -1;
        }

        String normalizedAnchor = anchor.trim();

        log.debug("Searching for {} anchor: [{}] in text of length {} (after: {}, before: {})",
                isStart ? "start" : "end", normalizedAnchor, chapterText.length(), afterPosition, beforePosition);

        // Tier 1: Exact match
        long position = findExactMatch(chapterText, normalizedAnchor, isStart, afterPosition, beforePosition);
        if (position != -1) {
            log.debug("Found exact match for anchor '{}' at position {}",
                    normalizedAnchor.length() > 20 ? normalizedAnchor.substring(0, 20) + "..." : normalizedAnchor,
                    position);
            return position;
        }

        // Tier 2: Word trimming
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

    private long findExactMatch(String chapterText, String anchor, boolean isStart, long afterPosition,
            long beforePosition) {
        int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
        int searchEnd = (beforePosition == -1) ? chapterText.length() : (int) beforePosition;

        if (searchStart >= searchEnd) {
            return -1;
        }

        String searchArea = chapterText.substring(searchStart, searchEnd);

        int pos = isStart ? searchArea.indexOf(anchor) : searchArea.lastIndexOf(anchor);

        if (pos == -1) {
            String normalizedAnchor = normalizeWhitespaceForComparison(anchor);
            String normalizedSearchArea = normalizeWhitespaceForComparison(searchArea);

            int normalizedPos = isStart ? normalizedSearchArea.indexOf(normalizedAnchor)
                    : normalizedSearchArea.lastIndexOf(normalizedAnchor);

            if (normalizedPos != -1) {
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

    private long findWithWordTrimming(String chapterText, String anchor, boolean isStart, long afterPosition,
            long beforePosition) {
        String[] words = anchor.split("\\s+");

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

        for (int wordCount = words.length - 1; wordCount >= MIN_WORDS_BEFORE_FUZZY; wordCount--) {
            String trimmedAnchor = String.join(" ", java.util.Arrays.copyOf(words, wordCount));

            List<Integer> matches = findAllMatchesInBounds(searchArea, trimmedAnchor, 0, searchArea.length());

            if (matches.isEmpty()) {
                String normalizedAnchor = normalizeWhitespaceForComparison(trimmedAnchor);
                String normalizedSearchArea = normalizeWhitespaceForComparison(searchArea);
                matches = findAllNormalizedMatchesInBounds(searchArea, normalizedSearchArea, normalizedAnchor, 0,
                        searchArea.length());
            }

            if (matches.size() == 1) {
                int pos = matches.get(0);
                log.debug("Found unique bounded match using {} words: '{}' at position {}",
                        wordCount, trimmedAnchor.length() > 30 ? trimmedAnchor.substring(0, 30) + "..." : trimmedAnchor,
                        pos);
                int actualPos = searchStart + pos;
                return isStart ? actualPos : actualPos + trimmedAnchor.length();
            } else if (matches.isEmpty()) {
                log.debug("No bounded match found for trimmed anchor: '{}'", trimmedAnchor);
                continue;
            } else {
                log.debug("Multiple bounded matches found for trimmed anchor '{}': {} occurrences within bounds",
                        trimmedAnchor, matches.size());
                continue;
            }
        }

        log.debug("Word trimming failed - no unique bounded match found");
        return -1;
    }

    private long findWithFuzzyMatching(String chapterText, String anchor, boolean isStart, long afterPosition,
            long beforePosition) {
        int anchorLength = anchor.length();
        int searchStart = (afterPosition == -1) ? 0 : (int) afterPosition;
        int searchEnd = (beforePosition == -1) ? chapterText.length() - anchorLength
                : (int) beforePosition - anchorLength;

        if (searchStart >= searchEnd) {
            return -1;
        }

        int bestPosition = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = searchStart; i <= searchEnd; i++) {
            String candidate = chapterText.substring(i, i + anchorLength);
            int distance = levenshteinDistance(anchor, candidate);

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

    private List<Integer> findAllMatchesInBounds(String text, String substring, int searchStart, int searchEnd) {
        List<Integer> matches = new ArrayList<>();

        if (searchStart >= searchEnd || searchStart < 0 || searchEnd > text.length()) {
            return matches;
        }

        String searchArea = text.substring(searchStart, searchEnd);
        int index = searchArea.indexOf(substring);

        while (index != -1 && searchStart + index + substring.length() <= searchEnd) {
            matches.add(searchStart + index);
            index = searchArea.indexOf(substring, index + 1);
        }

        return matches;
    }

    private List<Integer> findAllNormalizedMatchesInBounds(String originalText, String normalizedText,
            String normalizedSubstring, int searchStart, int searchEnd) {
        List<Integer> matches = new ArrayList<>();

        int index = normalizedText.indexOf(normalizedSubstring);

        while (index != -1) {
            int originalPos = mapNormalizedPositionToOriginal(originalText, normalizedText, index);

            if (originalPos >= searchStart && originalPos < searchEnd) {
                matches.add(originalPos);
            }

            index = normalizedText.indexOf(normalizedSubstring, index + 1);
        }

        return matches;
    }

    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                            dp[i - 1][j] + 1,
                            Math.min(
                                    dp[i][j - 1] + 1,
                                    dp[i - 1][j - 1] + 1));
                }
            }
        }

        return dp[len1][len2];
    }

    private String normalizeWhitespaceForComparison(String text) {
        if (text == null)
            return null;
        return text.replaceAll("\\s+", " ").trim();
    }

    private int mapNormalizedPositionToOriginal(String originalText, String normalizedText, int normalizedPos) {
        if (normalizedPos == 0) {
            for (int i = 0; i < originalText.length(); i++) {
                if (!Character.isWhitespace(originalText.charAt(i))) {
                    return i;
                }
            }
            return 0;
        }

        int originalPos = 0;
        int normalizedCount = 0;
        boolean inWhitespace = false;

        for (int i = 0; i < originalText.length() && normalizedCount < normalizedPos; i++) {
            char c = originalText.charAt(i);

            if (Character.isWhitespace(c)) {
                if (!inWhitespace) {
                    normalizedCount++;
                    inWhitespace = true;
                }
            } else {
                normalizedCount++;
                inWhitespace = false;
            }

            originalPos = i;

            if (normalizedCount >= normalizedPos) {
                return inWhitespace ? i : i + 1;
            }
        }

        return originalPos;
    }
}
