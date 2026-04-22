package com.lorevault.api.ai.application;

import com.lorevault.api.ai.domain.LlmRetryStrategy;
import com.lorevault.api.ai.domain.SceneLocalizationException;
import com.lorevault.api.ai.domain.LlmRetryStrategy.LlmRetryConfig;
import com.lorevault.api.ai.domain.LlmRetryStrategy.LlmRetryResult;
import com.lorevault.api.ai.domain.SceneDetectionResult;
import com.lorevault.api.ai.domain.SceneWithCoordinates;
import com.lorevault.api.ai.infrastructure.SceneDetectionClient;
import com.lorevault.api.content.domain.Chapter;
import com.lorevault.api.content.domain.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * - Triad analysis artifact generation (temporal linking runs post-persistence)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SceneDetectionService {

    public record SceneDetectionOutcome(
            List<SceneWithCoordinates> scenes,
            List<TriadOrchestrationService.TriadAnalysis> triadAnalyses,
            List<TriadOrchestrationService.TriadSceneIndividualExtraction> sceneIndividualExtractions,
            List<TriadOrchestrationService.TriadSceneLocationExtraction> sceneLocationExtractions,
            List<TriadOrchestrationService.TriadSceneEventExtraction> sceneEventExtractions
    ) {
        public SceneDetectionOutcome(
                List<SceneWithCoordinates> scenes,
                List<TriadOrchestrationService.TriadAnalysis> triadAnalyses,
                List<TriadOrchestrationService.TriadSceneIndividualExtraction> sceneIndividualExtractions,
                List<TriadOrchestrationService.TriadSceneLocationExtraction> sceneLocationExtractions
        ) {
            this(scenes, triadAnalyses, sceneIndividualExtractions, sceneLocationExtractions, List.of());
        }
    }

    private final SceneDetectionClient sceneDetectionClient;
    private final SceneProcessingService sceneProcessingService;
    private final LlmRetryStrategy llmRetryStrategy;
    private final TriadOrchestrationService triadOrchestrationService;

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
            return new SceneDetectionOutcome(Collections.emptyList(), List.of(), List.of(), List.of());
        }
        
        log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)", 
                 chapterId, jobId, chapterText.length());

        try {
            return detectScenesWithRetry(jobId, chapterId, chapterText, null);
        } catch (SceneLocalizationException e) {
            log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            throw e;
        } catch (Exception e) {
            if (isExpectedRetryableSegmentationFailure(e)) {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            } else {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            }
            throw new RuntimeException("Scene detection failed: " + e.getMessage(), e);
        }
    }

    public SceneDetectionOutcome detectScenesInChapter(UUID jobId, Chapter chapter) {
        if (chapter == null) {
            throw new IllegalArgumentException("chapter must not be null");
        }

        UUID chapterId = chapter.getId();
        String chapterText = chapter.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("Chapter {} has no text content for scene detection", chapterId);
            return new SceneDetectionOutcome(Collections.emptyList(), List.of(), List.of(), List.of());
        }

        log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)",
                chapterId, jobId, chapterText.length());

        try {
            return detectScenesWithRetry(jobId, chapterId, chapterText, chapter);
        } catch (SceneLocalizationException e) {
            log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            throw e;
        } catch (Exception e) {
            if (isExpectedRetryableSegmentationFailure(e)) {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            } else {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            }
            throw new RuntimeException("Scene detection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method: Detect scenes with retry logic and job status updates
     */
    private SceneDetectionOutcome detectScenesWithRetry(UUID jobId,
                                                        UUID chapterId,
                                                        String chapterText,
                                                        Chapter chapter) {

        // Configure retry strategy for scene detection
        LlmRetryConfig retryConfig = LlmRetryConfig.defaultConfig();

        log.info("Chapter segmentation starting with retry (max {} attempts) for job {}",
                retryConfig.getMaxAttempts(), jobId);

        // Execute scene detection with retry
        LlmRetryResult<SceneDetectionOutcome> retryResult = llmRetryStrategy.executeWithRetry(
                "Scene Detection",
                retryConfig,
                () -> performFullSceneDetection(jobId, chapterId, chapterText, chapter));

        if (retryResult.isSuccess()) {
        String successMsg = String.format("Chapter segmentation succeeded after %d/%d attempts in %d ms",
                    retryResult.getAttemptsUsed(), retryConfig.getMaxAttempts(),
                    retryResult.getTotalDurationMs());

            log.info("Scene detection successful for chapter {}: {}", chapterId, successMsg);
            return retryResult.getResult();

        } else {
        String failureMsg = String.format("Chapter segmentation failed after %d attempts in %d ms: %s",
                    retryResult.getAttemptsUsed(), retryResult.getTotalDurationMs(),
                    retryResult.getLastException().getMessage());

            if (retryResult.getLastException() instanceof SceneLocalizationException sceneLocalizationException) {
                throw sceneLocalizationException;
            }

            log.error("Scene detection failed for chapter {}: {}", chapterId, failureMsg);

            // Include retry attempt details in the exception for debugging
            String detailsMsg = String.join("; ", retryResult.getAttemptDetails());
            throw new RuntimeException(
                    "Scene detection failed with retry: " + failureMsg + " | Attempts: " + detailsMsg,
                    retryResult.getLastException());
        }
    }

    /**
     * Perform chapter segmentation and coordinate localization.
     */
    private SceneDetectionOutcome performFullSceneDetection(UUID jobId,
                                                            UUID chapterId,
                                                            String chapterText,
                                                            Chapter chapterMetadata) {
        try {
            // DEADLOCK RISK: do NOT call ingestionJobService.updateJobStatus() inside this method.
            // Outer tx (SceneDetectionHandler) holds a read-lock on IngestionJob; updateJobStatus
            // uses REQUIRES_NEW and needs a write-lock on the same node → Neo4j deadlock.
            log.info("Chapter segmentation: starting for job {} chapter {}", jobId, chapterId);

            SceneDetectionClient.SegmentationBudgetCheck budgetCheck = sceneDetectionClient.evaluateSegmentationBudget(chapterText);
            List<SegmentWindow> segments = createDeterministicSegments(
                    chapterText,
                    budgetCheck.estimatedTotalInput(),
                    budgetCheck.usableInputBudget()
            );

            if (segments.size() == 1 && budgetCheck.isWithinBudget()) {
                log.info("Segmentation budget check accepted for chapter {} (estimatedInput={}, budget={})",
                        chapterId, budgetCheck.estimatedTotalInput(), budgetCheck.usableInputBudget());
            } else {
                log.info("Segmentation budget check exceeded for chapter {} (estimatedInput={}, budget={}). Using segmented processing with {} segment(s).",
                        chapterId, budgetCheck.estimatedTotalInput(), budgetCheck.usableInputBudget(), segments.size());
            }

            List<SceneWithCoordinates> scenes = processSegments(jobId, segments);

            // Final validation
            if (scenes.isEmpty()) {
                throw new RuntimeException("Scene coordinate localization returned empty results");
            }

            // Triad scene analysis happens here only to produce reusable structured output artifacts.
            // Durable temporal linking is intentionally deferred to post-persistence stage.
            Chapter chapter = createTriadAnalysisChapter(chapterId, chapterText, chapterMetadata);
            var tempScenes = scenes.stream().map(s -> {
                String sceneText = null;
                try {
                    int start = (int) s.startCharacterOffset();
                    int end = (int) s.endCharacterOffset();
                    if (start >= 0 && end <= chapterText.length() && start < end) {
                        sceneText = chapterText.substring(start, end);
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract scene text for triad analysis: {}", e.getMessage());
                }

                return new Scene(
                        UUID.randomUUID(),
                        s.sceneIndex(),
                        s.startCharacterOffset(),
                        s.endCharacterOffset(),
                        s.contextSummary(),
                        s.chronology(),
                        s.chronologyCertainty(),
                        s.chronologyMarker(),
                        sceneText,
                        chapterId,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }).toList();

            for (var scene : tempScenes) {
                chapter.addExistingScene(scene);
            }

            var triadOutcome = triadOrchestrationService.analyzeChapterTriadsWithIndividuals(jobId, chapter);

            log.debug("Successfully completed scene segmentation/localization pipeline: {} scenes detected, {} triads analyzed",
                    scenes.size(), triadOutcome.triadAnalyses().size());
            return new SceneDetectionOutcome(
                    scenes,
                    triadOutcome.triadAnalyses(),
                    triadOutcome.sceneIndividualExtractions(),
                    triadOutcome.sceneLocationExtractions(),
                    triadOutcome.sceneEventExtractions()
            );

        } catch (Exception e) {
            if (isExpectedRetryableSegmentationFailure(e)) {
                log.warn("Triad-based scene detection pipeline failed: {}", e.getMessage());
            } else {
                log.error("Triad-based scene detection pipeline failed: {}", e.getMessage(), e);
            }
            throw e; // Re-throw to trigger retry
        }
    }

    private boolean isExpectedRetryableSegmentationFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SceneLocalizationException) {
                return true;
            }
            if (current instanceof RuntimeException && isKnownRetryableMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isKnownRetryableMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("Chapter segmentation parsing returned empty results")
                || message.contains("Scene coordinate localization returned empty results")
                || message.contains("Scene coordinate localization dropped scenes")
                || message.contains("Segmented fallback produced no localizable scenes")
                || message.contains("produced no localizable scenes")
                || message.contains("Scene detection failed with retry:")
                || message.contains("Chapter segmentation failed after");
    }

    private Chapter createTriadAnalysisChapter(UUID chapterId, String chapterText, Chapter chapterMetadata) {
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setRawText(chapterText);

        if (chapterMetadata != null) {
            chapter.setBookId(chapterMetadata.getBookId());
            chapter.setUniverseId(chapterMetadata.getUniverseId());
            chapter.setSeriesId(chapterMetadata.getSeriesId());
            chapter.setUniverse(chapterMetadata.getUniverse());
            chapter.setSeries(chapterMetadata.getSeries());
            chapter.setBookTitle(chapterMetadata.getBookTitle());
            chapter.setBookNumber(chapterMetadata.getBookNumber());
            chapter.setChapterNumber(chapterMetadata.getChapterNumber());
            chapter.setChapterTitle(chapterMetadata.getChapterTitle());
            chapter.setContentHash(chapterMetadata.getContentHash());
            chapter.setCoordinates(chapterMetadata.getCoordinates());
        }

        return chapter;
    }

    private List<SceneWithCoordinates> processSegments(UUID jobId, List<SegmentWindow> segments) {
        List<SceneWithCoordinates> rebasedScenes = new ArrayList<>();

        for (SegmentWindow segment : segments) {
            List<SceneWithCoordinates> localizedSegmentScenes = detectScenesInSingleSegment(jobId, segment.text());
            if (localizedSegmentScenes.isEmpty()) {
                throw new RuntimeException(String.format(
                        "Segment %d/%d produced no localizable scenes",
                        segment.segmentIndex() + 1,
                        segment.totalSegments()
                ));
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
                        localScene.chronology(),
                        localScene.chronologyCertainty(),
                        localScene.chronologyMarker(),
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
                    scene.chronology(),
                    scene.chronologyCertainty(),
                    scene.chronologyMarker(),
                    scene.potentialSplitSceneStart(),
                    scene.potentialSplitSceneEnd()
            ));
        }

        return renumbered;
    }

    private List<SceneWithCoordinates> detectScenesInSingleSegment(UUID jobId, String segmentText) {
        String segmentationXmlResponse = sceneDetectionClient.detectChapterSegmentation(jobId, segmentText);
        List<SceneDetectionResult> sceneResults = sceneProcessingService.parseSceneDetectionXml(segmentationXmlResponse, segmentText.length());
        if (sceneResults.isEmpty()) {
            throw new RuntimeException("Chapter segmentation parsing returned empty results - likely malformed XML response");
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

        if (localizedSceneCount != parsedSceneCount) {
            throw new RuntimeException(String.format(
                    "Scene coordinate localization dropped scenes (parsed=%d localized=%d)",
                    parsedSceneCount,
                    localizedSceneCount
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
