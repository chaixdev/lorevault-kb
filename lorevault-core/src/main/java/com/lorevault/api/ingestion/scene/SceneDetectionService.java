package com.lorevault.api.ingestion.scene;

import com.lorevault.api.ai.llm.LlmRetryStrategy;
import com.lorevault.api.ai.llm.LlmRetryStrategy.LlmRetryConfig;
import com.lorevault.api.ai.llm.LlmRetryStrategy.LlmRetryResult;
import com.lorevault.api.ai.llm.LlmClient;
import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.ingestion.job.IngestionFailure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI-backed scene detection as part of the ingestion scene stage.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SceneDetectionService {

    public record SceneSegmentationOutcome(List<SceneWithCoordinates> scenes) {}

    private final LlmClient llmClient;
    private final SceneProcessingService sceneProcessingService;
    private final LlmRetryStrategy llmRetryStrategy;

    public SceneSegmentationOutcome detectScenesInText(UUID jobId, UUID chapterId, String chapterText) {
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("Chapter {} has no text content for scene detection", chapterId);
            return new SceneSegmentationOutcome(Collections.emptyList());
        }

        log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)",
                chapterId, jobId, chapterText.length());

        try {
            return detectScenesWithRetry(jobId, chapterId, chapterText, null);
        } catch (SceneLocalizationException e) {
            log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            throw e;
        } catch (SceneDetectionException e) {
            log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            throw e;
        } catch (Exception e) {
            if (isExpectedRetryableSegmentationFailure(e)) {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            } else {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            }
            throw buildSceneDetectionFailure(
                    "SCENE_DETECTION_FAILED",
                    "Scene detection failed: " + safeMessage(e),
                    chapterId,
                    e
            );
        }
    }

    public SceneSegmentationOutcome detectScenesInChapter(UUID jobId, Chapter chapter) {
        if (chapter == null) {
            throw new IllegalArgumentException("chapter must not be null");
        }

        UUID chapterId = chapter.getId();
        String chapterText = chapter.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("Chapter {} has no text content for scene detection", chapterId);
            return new SceneSegmentationOutcome(Collections.emptyList());
        }

        log.info("Starting scene detection with retry for chapter {} (job {}, length={} chars)",
                chapterId, jobId, chapterText.length());

        try {
            return detectScenesWithRetry(jobId, chapterId, chapterText, chapter);
        } catch (SceneLocalizationException e) {
            log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            throw e;
        } catch (SceneDetectionException e) {
            log.warn("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            throw e;
        } catch (Exception e) {
            if (isExpectedRetryableSegmentationFailure(e)) {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage());
            } else {
                log.error("Scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            }
            throw buildSceneDetectionFailure(
                    "SCENE_DETECTION_FAILED",
                    "Scene detection failed: " + safeMessage(e),
                    chapterId,
                    e
            );
        }
    }

    private SceneSegmentationOutcome detectScenesWithRetry(UUID jobId,
                                                           UUID chapterId,
                                                           String chapterText,
                                                           Chapter chapter) {
        LlmRetryConfig retryConfig = LlmRetryConfig.defaultConfig();

        log.info("Chapter segmentation starting with retry (max {} attempts) for job {}",
                retryConfig.getMaxAttempts(), jobId);

        LlmRetryResult<SceneSegmentationOutcome> retryResult = llmRetryStrategy.executeWithRetry(
                "Scene Detection",
                retryConfig,
                () -> performFullSceneDetection(jobId, chapterId, chapterText, chapter));

        if (retryResult.isSuccess()) {
            String successMsg = String.format("Chapter segmentation succeeded after %d/%d attempts in %d ms",
                    retryResult.getAttemptsUsed(), retryConfig.getMaxAttempts(), retryResult.getTotalDurationMs());

            log.info("Scene detection successful for chapter {}: {}", chapterId, successMsg);
            return retryResult.getResult();
        }

        String failureMsg = String.format("Chapter segmentation failed after %d attempts in %d ms: %s",
                retryResult.getAttemptsUsed(), retryResult.getTotalDurationMs(),
                retryResult.getLastException().getMessage());

        if (retryResult.getLastException() instanceof SceneLocalizationException sceneLocalizationException) {
            throw sceneLocalizationException;
        }

        log.error("Scene detection failed for chapter {}: {}", chapterId, failureMsg);
        String detailsMsg = String.join("; ", retryResult.getAttemptDetails());
        throw buildSceneDetectionFailure(
                "SCENE_DETECTION_RETRY_EXHAUSTED",
                "Scene detection failed with retry: " + failureMsg + " | Attempts: " + detailsMsg,
                chapterId,
                retryResult.getLastException()
        );
    }

    private SceneSegmentationOutcome performFullSceneDetection(UUID jobId,
                                                               UUID chapterId,
                                                               String chapterText,
                                                               Chapter chapterMetadata) {
        try {
            log.info("Chapter segmentation: starting for job {} chapter {}", jobId, chapterId);

            LlmClient.SegmentationBudgetCheck budgetCheck = llmClient.evaluateSegmentationBudget(chapterText);
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

            List<SceneWithCoordinates> scenes = processSegments(jobId, chapterId, segments);
            if (scenes.isEmpty()) {
                throw buildSceneDetectionFailure(
                        "SCENE_COORDINATE_LOCALIZATION_EMPTY",
                        "Scene coordinate localization returned empty results",
                        chapterId,
                        null
                );
            }

            log.debug("Successfully completed scene segmentation/localization pipeline: {} scenes detected",
                    scenes.size());
            return new SceneSegmentationOutcome(scenes);
        } catch (Exception e) {
            if (isExpectedRetryableSegmentationFailure(e)) {
                log.warn("Triad-based scene detection pipeline failed: {}", e.getMessage());
            } else {
                log.error("Triad-based scene detection pipeline failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }

    private boolean isExpectedRetryableSegmentationFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SceneLocalizationException) {
                return true;
            }
            if (current instanceof SceneDetectionException sceneDetectionException
                    && sceneDetectionException.failure() != null) {
                String code = sceneDetectionException.failure().code();
                if ("SCENE_DETECTION_RETRY_EXHAUSTED".equals(code)
                        || "SCENE_SEGMENT_NO_LOCALIZABLE_SCENES".equals(code)
                        || "SCENE_SEGMENTED_FALLBACK_EMPTY".equals(code)
                        || "SCENE_SEGMENTATION_XML_EMPTY".equals(code)
                        || "SCENE_COORDINATE_LOCALIZATION_EMPTY".equals(code)
                        || "SCENE_COORDINATE_LOCALIZATION_DROPPED_SCENES".equals(code)) {
                    return true;
                }
            }
            if (current instanceof RuntimeException && isKnownRetryableMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private SceneDetectionException buildSceneDetectionFailure(String code,
                                                               String message,
                                                               UUID chapterId,
                                                               Throwable cause) {
        IngestionFailure failure = IngestionFailure.builder(code, message)
                .exceptionType(cause != null ? cause.getClass().getSimpleName() : null)
                .stage("SCENE_DETECTION")
                .detail("chapterId", chapterId)
                .build();
        return new SceneDetectionException(failure, cause);
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown scene detection failure";
        }
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
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

    private List<SceneWithCoordinates> processSegments(UUID jobId, UUID chapterId, List<SegmentWindow> segments) {
        List<SceneWithCoordinates> rebasedScenes = new ArrayList<>();

        for (SegmentWindow segment : segments) {
            List<SceneWithCoordinates> localizedSegmentScenes = detectScenesInSingleSegment(jobId, chapterId, segment.text());
            if (localizedSegmentScenes.isEmpty()) {
                throw buildSceneDetectionFailure(
                        "SCENE_SEGMENT_NO_LOCALIZABLE_SCENES",
                        String.format(
                                "Segment %d/%d produced no localizable scenes",
                                segment.segmentIndex() + 1,
                                segment.totalSegments()
                        ),
                        chapterId,
                        null
                );
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
            throw buildSceneDetectionFailure(
                    "SCENE_SEGMENTED_FALLBACK_EMPTY",
                    "Segmented fallback produced no localizable scenes",
                    chapterId,
                    null
            );
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

    private List<SceneWithCoordinates> detectScenesInSingleSegment(UUID jobId, UUID chapterId, String segmentText) {
        String segmentationXmlResponse = llmClient.detectChapterSegmentation(jobId, segmentText);
        List<SceneDetectionResult> sceneResults = sceneProcessingService.parseSceneDetectionXml(segmentationXmlResponse, segmentText.length());
        if (sceneResults.isEmpty()) {
            throw buildSceneDetectionFailure(
                    "SCENE_SEGMENTATION_XML_EMPTY",
                    "Chapter segmentation parsing returned empty results - likely malformed XML response",
                    chapterId,
                    null
            );
        }
        List<SceneWithCoordinates> scenes = sceneProcessingService.localizeSceneCoordinates(segmentText, sceneResults);
        if (scenes.isEmpty()) {
            throw buildSceneDetectionFailure(
                    "SCENE_COORDINATE_LOCALIZATION_EMPTY",
                    "Scene coordinate localization returned empty results",
                    chapterId,
                    null
            );
        }
        validateLocalizationCoverage(chapterId, sceneResults.size(), scenes.size());
        return scenes;
    }

    private void validateLocalizationCoverage(UUID chapterId, int parsedSceneCount, int localizedSceneCount) {
        if (parsedSceneCount <= 0) {
            return;
        }

        if (localizedSceneCount != parsedSceneCount) {
            throw buildSceneDetectionFailure(
                    "SCENE_COORDINATE_LOCALIZATION_DROPPED_SCENES",
                    String.format(
                            "Scene coordinate localization dropped scenes (parsed=%d localized=%d)",
                            parsedSceneCount,
                            localizedSceneCount
                    ),
                    chapterId,
                    null
            );
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
