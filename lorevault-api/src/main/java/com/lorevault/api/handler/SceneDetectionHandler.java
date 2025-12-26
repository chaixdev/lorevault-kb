package com.lorevault.api.handler;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.JobContextPort;
import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.event.ingestion.ChapterPersistedEvent;
import com.lorevault.api.event.ingestion.IngestionFailedEvent;
import com.lorevault.api.event.ingestion.ScenesDetectedEvent;
import com.lorevault.api.service.content.SceneProcessingService;
import com.lorevault.api.service.ingestion.IngestionJobService;
import com.lorevault.api.service.timeline.DefaultTemporalEdgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Handler for scene detection stage of the ingestion pipeline.
 * 
 * Listens to: ChapterPersistedEvent
 * Emits: ScenesDetectedEvent (on success) or IngestionFailedEvent (on failure)
 * 
 * Responsibilities:
 * - Use AI to detect semantic scene boundaries in chapter text
 * - Persist detected scenes to the database
 * - Create default temporal edges between scenes
 * - Update job status throughout the process
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SceneDetectionHandler {

    private final ContentPersistencePort contentPersistencePort;
    private final SceneDetectionPort sceneDetectionPort;
    private final SceneProcessingService sceneProcessingService;
    private final IngestionJobService ingestionJobService;
    private final JobContextPort jobContextPort;
    private final DefaultTemporalEdgeService defaultTemporalEdgeService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleChapterPersisted(ChapterPersistedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();
        
        log.info("[SCENE_DETECTION] Starting for job={}, chapter={}", jobId, chapterId);
        
        try {
            // Set job context for retry-aware operations
            jobContextPort.setCurrentJobId(jobId);
            
            updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION, 
                    "Analyzing chapter text with AI to identify semantic scene boundaries");

            // Check for existing scenes (idempotency)
            List<Scene> existingScenes = contentPersistencePort.findScenesByChapterId(chapterId);
            if (!existingScenes.isEmpty()) {
                log.info("[SCENE_DETECTION] Found {} existing scenes for chapter {}, skipping detection", 
                        existingScenes.size(), chapterId);
                emitScenesDetected(jobId, chapterId, bookId, existingScenes);
                return;
            }

            // Detect and persist new scenes
            List<Scene> scenes = detectAndPersistScenes(chapterId);
            
            if (scenes.isEmpty()) {
                log.warn("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            }
            
            // Create default temporal edges
            log.info("[SCENE_DETECTION] Creating default temporal edges for book {}", bookId);
            defaultTemporalEdgeService.createAllDefaults(bookId);
            
            updateJobStatus(jobId, IngestionStatus.SCENE_SEGMENTATION,
                    String.format("Detected %d semantic scenes from chapter text", scenes.size()));
            
            emitScenesDetected(jobId, chapterId, bookId, scenes);
            
        } catch (Exception e) {
            log.error("[SCENE_DETECTION] Failed for job={}, chapter={}: {}", 
                    jobId, chapterId, e.getMessage(), e);
            emitFailure(jobId, chapterId, "SCENE_DETECTION", e);
        } finally {
            jobContextPort.clearCurrentJobId();
        }
    }

    private List<Scene> detectAndPersistScenes(UUID chapterId) {
        log.info("[SCENE_DETECTION] Detecting scenes for chapter {}", chapterId);

        Chapter chapter = contentPersistencePort.findChapterById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        String chapterText = chapter.getRawText();
        if (chapterText == null || chapterText.trim().isEmpty()) {
            log.warn("[SCENE_DETECTION] Chapter {} has no text content", chapterId);
            return List.of();
        }

        // Use AI to detect scenes
        var scenesWithCoords = sceneDetectionPort.detectScenesInText(chapterId, chapterText);

        if (scenesWithCoords.isEmpty()) {
            log.info("[SCENE_DETECTION] No scenes detected for chapter {}", chapterId);
            return List.of();
        }

        // Persist detected scenes
        return sceneProcessingService.persistDetectedScenes(chapterId, scenesWithCoords);
    }

    private void emitScenesDetected(UUID jobId, UUID chapterId, UUID bookId, List<Scene> scenes) {
        List<UUID> sceneIds = scenes.stream().map(Scene::getId).toList();
        
        log.info("[SCENE_DETECTION] Emitting ScenesDetectedEvent: job={}, chapter={}, sceneCount={}", 
                jobId, chapterId, scenes.size());
        
        eventPublisher.publishEvent(new ScenesDetectedEvent(this, jobId, chapterId, bookId, sceneIds));
    }

    private void emitFailure(UUID jobId, UUID chapterId, String stage, Exception e) {
        boolean retryable = isRetryableError(e);
        
        eventPublisher.publishEvent(new IngestionFailedEvent(
                this, jobId, chapterId, stage, e.getMessage(), retryable));
        
        // Also update job status to failed
        ingestionJobService.updateJobStatus(jobId, IngestionStatus.FAILED, 
                stage + " failed: " + e.getMessage(), java.util.Collections.emptyMap());
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("LLM API") || 
                message.contains("scene detection failed") || 
                message.contains("Empty response") || 
                message.contains("timeout") ||
                message.contains("rate limit"));
    }

    private void updateJobStatus(UUID jobId, IngestionStatus status, String description) {
        ingestionJobService.updateJobStatus(jobId, status, description, java.util.Collections.emptyMap());
    }
}
