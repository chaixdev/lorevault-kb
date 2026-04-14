package com.lorevault.api.ai;

import com.lorevault.api.ai.LlmRetryStrategy.LlmRetryConfig;
import com.lorevault.api.ai.LlmRetryStrategy.LlmRetryResult;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.timeline.TriadEdgePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Service for AI-powered scene detection within chapter text.
 * 
 * This is a business logic service that orchestrates:
 * - LLM calls via SceneDetectionClient
 * - XML parsing and coordinate localization via SceneProcessingService
 * - Retry handling with status updates
 * - Triad analysis for temporal relationships
 */
@Service
public class SceneDetectionService {

    private static final Logger log = LoggerFactory.getLogger(SceneDetectionService.class);
    private static final double MIN_LOCALIZATION_SUCCESS_RATIO = 0.8;

    public record SceneDetectionOutcome(
            List<SceneWithCoordinates> scenes,
            List<TriadOrchestrationService.TriadSceneIndividualExtraction> sceneIndividualExtractions,
            List<TriadOrchestrationService.TriadSceneLocationExtraction> sceneLocationExtractions
    ) {}

    private final SceneDetectionClient sceneDetectionClient;
    private final SceneProcessingService sceneProcessingService;
    private final LlmRetryStrategy llmRetryStrategy;
    private final TriadOrchestrationService triadOrchestrationService;
    private final TriadEdgePersistenceService triadEdgePersistenceService;

    public SceneDetectionService(SceneDetectionClient sceneDetectionClient,
                                 SceneProcessingService sceneProcessingService,
                                 LlmRetryStrategy llmRetryStrategy,
                                 TriadOrchestrationService triadOrchestrationService,
                                 TriadEdgePersistenceService triadEdgePersistenceService) {
        this.sceneDetectionClient = sceneDetectionClient;
        this.sceneProcessingService = sceneProcessingService;
        this.llmRetryStrategy = llmRetryStrategy;
        this.triadOrchestrationService = triadOrchestrationService;
        this.triadEdgePersistenceService = triadEdgePersistenceService;
    }

    /**
     * Detect semantic scenes within chapter text using AI.
     * 
     * @param jobId The UUID of the ingestion job (for status tracking)
     * @param chapterId The UUID of the chapter
     * @param chapterText The full text content to analyze
     * @return List of detected scenes with their coordinates
     * @throws RuntimeException if the detection process fails
     */
    public SceneDetectionOutcome detectScenesInText(UUID jobId, UUID chapterId, String chapterText) {
        // Handle null or empty text gracefully
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("Chapter {} has no text content for scene detection", chapterId);
            return new SceneDetectionOutcome(Collections.emptyList(), List.of(), List.of());
        }
        
        log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)", 
                 chapterId, jobId, chapterText.length());

        try {
            return detectScenesWithRetry(jobId, chapterId, chapterText);
        } catch (Exception e) {
            log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            throw new RuntimeException("Scene detection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method: Detect scenes with retry logic and job status updates
     */
    private SceneDetectionOutcome detectScenesWithRetry(UUID jobId, UUID chapterId, String chapterText) {

        // Configure retry strategy for scene detection
        LlmRetryConfig retryConfig = LlmRetryConfig.defaultConfig();

        log.info("Scene segmentation (Pass 1) starting with retry (max {} attempts) for job {}",
                retryConfig.getMaxAttempts(), jobId);

        // Execute scene detection with retry
        LlmRetryResult<SceneDetectionOutcome> retryResult = llmRetryStrategy.executeWithRetry(
                "Scene Detection",
                retryConfig,
                () -> performFullSceneDetection(jobId, chapterId, chapterText));

        if (retryResult.isSuccess()) {
        String successMsg = String.format("Scene segmentation (Pass 1) succeeded after %d/%d attempts in %d ms",
                    retryResult.getAttemptsUsed(), retryConfig.getMaxAttempts(),
                    retryResult.getTotalDurationMs());

            log.info("Scene detection successful for chapter {}: {}", chapterId, successMsg);
            return retryResult.getResult();

        } else {
        String failureMsg = String.format("Scene segmentation (Pass 1) failed after %d attempts in %d ms: %s",
                    retryResult.getAttemptsUsed(), retryResult.getTotalDurationMs(),
                    retryResult.getLastException().getMessage());

            log.error("Scene detection failed for chapter {}: {}", chapterId, failureMsg);

            // Include retry attempt details in the exception for debugging
            String detailsMsg = String.join("; ", retryResult.getAttemptDetails());
            throw new RuntimeException(
                    "Scene detection failed with retry: " + failureMsg + " | Attempts: " + detailsMsg,
                    retryResult.getLastException());
        }
    }

    /**
     * Perform the complete scene detection pipeline (Pass 1 + triad-based Pass 2 + parsing + localization)
     */
    private SceneDetectionOutcome performFullSceneDetection(UUID jobId, UUID chapterId, String chapterText) {
        try {
            // DEADLOCK RISK: do NOT call ingestionJobService.updateJobStatus() inside this method.
            // Outer tx (SceneDetectionHandler) holds a read-lock on IngestionJob; updateJobStatus
            // uses REQUIRES_NEW and needs a write-lock on the same node → Neo4j deadlock.
            log.info("Scene segmentation (Pass 1): starting for job {} chapter {}", jobId, chapterId);

            SceneDetectionClient.Pass1BudgetCheck budgetCheck = sceneDetectionClient.evaluatePass1Budget(chapterText);
            List<SegmentWindow> segments = createDeterministicSegments(
                    chapterText,
                    budgetCheck.estimatedTotalInput(),
                    budgetCheck.usableInputBudget()
            );

            if (segments.size() == 1 && budgetCheck.isWithinBudget()) {
                log.info("Pass 1 budget check accepted for chapter {} (estimatedInput={}, budget={})",
                        chapterId, budgetCheck.estimatedTotalInput(), budgetCheck.usableInputBudget());
            } else {
                log.info("Pass 1 budget check exceeded for chapter {} (estimatedInput={}, budget={}). Using segmented processing with {} segment(s).",
                        chapterId, budgetCheck.estimatedTotalInput(), budgetCheck.usableInputBudget(), segments.size());
            }

            List<SceneWithCoordinates> scenes = processSegments(jobId, segments);

            // Final validation
            if (scenes.isEmpty()) {
                throw new RuntimeException("Scene coordinate localization returned empty results");
            }

            // Triad Pass 2: analyze prev/curr/next scene triads and persist TEMPORAL edges
            // This replaces the old Pass 2 approach that sent full XML to LLM
            Chapter chapter = Chapter.createWithReferences(
                    null, null, null, null, null, chapterText, null
            );
            new BeanWrapperImpl(chapter).setPropertyValue("id", chapterId);
            
            // Convert SceneWithCoordinates to Scene objects temporarily for triad analysis
            // (without persisting - that will happen later in the ingestion pipeline)
            var tempScenes = scenes.stream().map(s -> {
                String sceneText = null;
                if (chapterText != null) {
                    try {
                        int start = (int) s.startCharacterOffset();
                        int end = (int) s.endCharacterOffset();
                        if (start >= 0 && end <= chapterText.length() && start < end) {
                            sceneText = chapterText.substring(start, end);
                        }
                    } catch (Exception e) {
                        log.debug("Failed to extract scene text for triad analysis: {}", e.getMessage());
                    }
                }

                Scene scene = new Scene(
                        UUID.randomUUID(),
                        s.sceneIndex(),
                        s.startCharacterOffset(),
                        s.endCharacterOffset(),
                        s.contextSummary(),
                        sceneText,
                        chapterId,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                return scene;
            }).toList();
            
            // Add scenes to chapter for triad analysis (in-memory only)
            for (var scene : tempScenes) {
                chapter.addExistingScene(scene);
            }

            // Run triad orchestration with populated chapter
            log.info("Triad analysis (Pass 2): starting for job {} chapter {}", jobId, chapterId);
            var triadOutcome = triadOrchestrationService.analyzeChapterTriadsWithIndividuals(jobId, chapter);
            triadEdgePersistenceService.applyTriadAnalyses(triadOutcome.triadAnalyses());

            log.debug("Successfully completed triad-based scene detection pipeline: {} scenes detected, {} triad analyses completed", 
                     scenes.size(), triadOutcome.triadAnalyses().size());
            return new SceneDetectionOutcome(
                    scenes,
                    triadOutcome.sceneIndividualExtractions(),
                    triadOutcome.sceneLocationExtractions()
            );

        } catch (Exception e) {
            // Log the specific stage that failed for debugging with full stack trace
            log.error("Triad-based scene detection pipeline failed: {}", e.getMessage(), e);
            throw e; // Re-throw to trigger retry
        }
    }

    private List<SceneWithCoordinates> processSegments(UUID jobId, List<SegmentWindow> segments) {
        List<SceneWithCoordinates> rebasedScenes = new ArrayList<>();

        for (SegmentWindow segment : segments) {
            List<SceneWithCoordinates> localizedSegmentScenes = detectScenesInSingleSegment(jobId, segment.text());
            if (localizedSegmentScenes.isEmpty()) {
                continue;
            }

            List<SceneWithCoordinates> sortedSegmentScenes = localizedSegmentScenes.stream()
                    .sorted(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset))
                    .toList();

            for (int i = 0; i < sortedSegmentScenes.size(); i++) {
                SceneWithCoordinates localScene = sortedSegmentScenes.get(i);
                boolean potentialSplitStart = segment.segmentIndex() > 0 && i == 0;
                boolean potentialSplitEnd = segment.segmentIndex() < segment.totalSegments() - 1
                        && i == sortedSegmentScenes.size() - 1;

                rebasedScenes.add(new SceneWithCoordinates(
                        0,
                        segment.startOffset() + localScene.startCharacterOffset(),
                        segment.startOffset() + localScene.endCharacterOffset(),
                        localScene.contextSummary(),
                        potentialSplitStart,
                        potentialSplitEnd
                ));
            }
        }

        if (rebasedScenes.isEmpty()) {
            throw new RuntimeException("Segmented fallback produced no localizable scenes");
        }

        List<SceneWithCoordinates> ordered = rebasedScenes.stream()
                .sorted(Comparator.comparingLong(SceneWithCoordinates::startCharacterOffset))
                .toList();

        List<SceneWithCoordinates> renumbered = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            SceneWithCoordinates scene = ordered.get(i);
            renumbered.add(new SceneWithCoordinates(
                    i,
                    scene.startCharacterOffset(),
                    scene.endCharacterOffset(),
                    scene.contextSummary(),
                    scene.potentialSplitSceneStart(),
                    scene.potentialSplitSceneEnd()
            ));
        }

        return renumbered;
    }

    private List<SceneWithCoordinates> detectScenesInSingleSegment(UUID jobId, String segmentText) {
        String pass1XmlResponse = sceneDetectionClient.detectScenesPass1(jobId, segmentText);
        List<SceneDetectionResult> sceneResults = sceneProcessingService.parseSceneDetectionXml(pass1XmlResponse, segmentText.length());
        if (sceneResults.isEmpty()) {
            throw new RuntimeException("Pass 1 scene detection parsing returned empty results - likely malformed XML response");
        }
        List<SceneWithCoordinates> scenes = sceneProcessingService.localizeSceneCoordinates(segmentText, sceneResults);
        if (scenes.isEmpty()) {
            throw new RuntimeException("Scene coordinate localization returned empty results");
        }
        validateLocalizationCoverage(sceneResults.size(), scenes.size());
        return scenes;
    }

    private void validateLocalizationCoverage(int parsedSceneCount, int localizedSceneCount) {
        if (parsedSceneCount <= 0) {
            return;
        }

        double successRatio = (double) localizedSceneCount / parsedSceneCount;
        if (successRatio < MIN_LOCALIZATION_SUCCESS_RATIO) {
            throw new RuntimeException(String.format(
                    "Scene coordinate localization dropped too many scenes (parsed=%d localized=%d successRatio=%.2f threshold=%.2f)",
                    parsedSceneCount,
                    localizedSceneCount,
                    successRatio,
                    MIN_LOCALIZATION_SUCCESS_RATIO
            ));
        }
    }

    private List<SegmentWindow> createDeterministicSegments(String text, int estimatedTotalInput, int usableInputBudget) {
        if (usableInputBudget <= 0) {
            throw new IllegalArgumentException("usableInputBudget must be greater than zero");
        }

        int segmentCount = Math.max(1, (int) Math.ceil((double) estimatedTotalInput / usableInputBudget));
        if (segmentCount == 1) {
            return List.of(new SegmentWindow(0, text.length(), text, 0, 1));
        }

        List<SegmentWindow> segments = new ArrayList<>();
        int textLength = text.length();
        int previousCut = 0;

        for (int i = 1; i < segmentCount; i++) {
            int idealCut = (int) Math.round((double) textLength * i / segmentCount);
            int cut = findPreferredBoundary(text, previousCut, textLength, idealCut);
            if (cut <= previousCut || cut >= textLength) {
                cut = Math.min(textLength - 1, Math.max(previousCut + 1, idealCut));
            }

            segments.add(new SegmentWindow(previousCut, cut, text.substring(previousCut, cut), segments.size(), segmentCount));
            previousCut = cut;
        }

        if (previousCut < textLength) {
            segments.add(new SegmentWindow(previousCut, textLength, text.substring(previousCut), segments.size(), segmentCount));
        }

        return segments;
    }

    private int findPreferredBoundary(String text, int startBound, int endBound, int idealCut) {
        int window = Math.max(200, (endBound - startBound) / 8);

        int cut = findNearestPatternBoundary(text, "\n\n", startBound, endBound, idealCut, window, 2);
        if (cut != -1) return cut;

        cut = findNearestPatternBoundary(text, "\n", startBound, endBound, idealCut, window, 1);
        if (cut != -1) return cut;

        cut = findNearestSentenceBoundary(text, startBound, endBound, idealCut, window);
        if (cut != -1) return cut;

        cut = findNearestWhitespaceBoundary(text, startBound, endBound, idealCut, window);
        if (cut != -1) return cut;

        return idealCut;
    }

    private int findNearestPatternBoundary(String text, String pattern, int startBound, int endBound,
                                           int idealCut, int window, int advance) {
        int searchStart = Math.max(startBound, idealCut - window);
        int searchEnd = Math.min(endBound, idealCut + window);

        int nearest = -1;
        int bestDistance = Integer.MAX_VALUE;
        int index = text.indexOf(pattern, searchStart);
        while (index != -1 && index < searchEnd) {
            int cut = index + advance;
            if (cut > startBound && cut < endBound) {
                int distance = Math.abs(cut - idealCut);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest = cut;
                }
            }
            index = text.indexOf(pattern, index + 1);
        }

        return nearest;
    }

    private int findNearestSentenceBoundary(String text, int startBound, int endBound, int idealCut, int window) {
        int searchStart = Math.max(startBound + 1, idealCut - window);
        int searchEnd = Math.min(endBound - 1, idealCut + window);

        int nearest = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = searchStart; i < searchEnd; i++) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i + 1))) {
                int cut = i + 1;
                int distance = Math.abs(cut - idealCut);
                if (distance < bestDistance && cut > startBound && cut < endBound) {
                    bestDistance = distance;
                    nearest = cut;
                }
            }
        }

        return nearest;
    }

    private int findNearestWhitespaceBoundary(String text, int startBound, int endBound, int idealCut, int window) {
        int searchStart = Math.max(startBound + 1, idealCut - window);
        int searchEnd = Math.min(endBound - 1, idealCut + window);

        int nearest = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = searchStart; i < searchEnd; i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                int distance = Math.abs(i - idealCut);
                if (distance < bestDistance && i > startBound && i < endBound) {
                    bestDistance = distance;
                    nearest = i;
                }
            }
        }

        return nearest;
    }

    private record SegmentWindow(int startOffset, int endOffset, String text, int segmentIndex, int totalSegments) {
    }
}
