package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.chunk.ChunkGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.scene.SceneGraphRepository;
import com.lorevault.api.ingestion.events.BookCollectivesReducedEvent;
import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.BookObjectsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterCollectivesResolvedEvent;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
import com.lorevault.api.ingestion.events.ChapterObjectsResolvedEvent;
import com.lorevault.api.ingestion.events.ChunksCreatedEvent;
import com.lorevault.api.ingestion.events.EmbeddingsCompletedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ingestion.pipeline.StepKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps {@link StepKey} enum values to their corresponding domain events
 * when {@code fireEvents=true} is set on a step execution request.
 *
 * <p>Phase 1 implements DETECT_SCENES, CHUNK, EMBED, RESOLVE_EVENTS.
 * Phase 3 adds RESOLVE_INDIVIDUALS, RESOLVE_COLLECTIVES, RESOLVE_LOCATIONS,
 * RESOLVE_OBJECTS, REDUCE_INDIVIDUALS, REDUCE_COLLECTIVES, REDUCE_LOCATIONS,
 * and REDUCE_OBJECTS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StepEventMapper {

    private final ApplicationEventPublisher eventPublisher;
    private final ChapterGraphRepository chapterGraphRepository;
    private final SceneGraphRepository sceneGraphRepository;
    private final ChunkGraphRepository chunkGraphRepository;

    /**
     * Publishes a domain event (if one exists) for the given completed step.
     *
     * @param stepKey the step that just completed
     * @param jobId   the ingestion job identifier
     * @param scopeId the chapter or book ID that was processed
     * @param result  the outcome of the step execution
     */
    public void publishCompletionEvent(StepKey stepKey, UUID jobId, UUID scopeId, StepResult result) {
        switch (stepKey) {
            case DETECT_SCENES -> publishScenesDetectedEvent(jobId, scopeId);
            case CHUNK -> publishChunksCreatedEvent(jobId, scopeId, result);
            case EMBED -> publishEmbeddingsCompletedEvent(jobId, scopeId, result);
            case RESOLVE_EVENTS -> publishChapterEventsResolvedEvent(jobId, scopeId, result);
            case RESOLVE_INDIVIDUALS -> publishChapterIndividualsResolvedEvent(jobId, scopeId, result);
            case RESOLVE_COLLECTIVES -> publishChapterCollectivesResolvedEvent(jobId, scopeId, result);
            case RESOLVE_LOCATIONS -> publishChapterLocationsResolvedEvent(jobId, scopeId, result);
            case RESOLVE_OBJECTS -> publishChapterObjectsResolvedEvent(jobId, scopeId, result);
            case REDUCE_INDIVIDUALS -> publishBookIndividualsReducedEvent(jobId, scopeId, result);
            case REDUCE_COLLECTIVES -> publishBookCollectivesReducedEvent(jobId, scopeId, result);
            case REDUCE_LOCATIONS -> publishBookLocationsReducedEvent(jobId, scopeId, result);
            case REDUCE_OBJECTS -> publishBookObjectsReducedEvent(jobId, scopeId, result);
        }
    }

    private void publishScenesDetectedEvent(UUID jobId, UUID chapterId) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish ScenesDetectedEvent", chapterId);
            return;
        }

        UUID bookId = chapterOpt.get().getBookId();
        List<Scene> scenes = sceneGraphRepository.findByChapterId(chapterId);
        List<UUID> sceneIds = scenes.stream()
                .map(Scene::getEventId)
                .toList();

        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, bookId, sceneIds);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published ScenesDetectedEvent: jobId={}, chapterId={}, bookId={}, scenes={}",
                jobId, chapterId, bookId, sceneIds.size());
    }

    private void publishChunksCreatedEvent(UUID jobId, UUID chapterId, StepResult result) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish ChunksCreatedEvent", chapterId);
            return;
        }

        UUID bookId = chapterOpt.get().getBookId();
        int chunkCount = result.counts().getOrDefault("chunksCreated", 0);

        ChunksCreatedEvent event = new ChunksCreatedEvent(this, jobId, chapterId, bookId, chunkCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published ChunksCreatedEvent: jobId={}, chapterId={}, bookId={}, chunks={}",
                jobId, chapterId, bookId, chunkCount);
    }

    private void publishEmbeddingsCompletedEvent(UUID jobId, UUID chapterId, StepResult result) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish EmbeddingsCompletedEvent", chapterId);
            return;
        }

        Chapter chapter = chapterOpt.get();
        int totalScenes = sceneGraphRepository.findByChapterId(chapterId).size();
        int totalChunks = chunkGraphRepository.countByChapterId(chapterId);
        int totalEmbeddings = result.counts().getOrDefault("embeddingsGenerated", 0);
        int chapterLength = chapter.getRawText() != null ? chapter.getRawText().length() : 0;

        EmbeddingsCompletedEvent event = new EmbeddingsCompletedEvent(
                this, jobId, chapterId, totalScenes, totalChunks, totalEmbeddings, chapterLength);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published EmbeddingsCompletedEvent: jobId={}, chapterId={}, scenes={}, chunks={}, embeddings={}, length={}",
                jobId, chapterId, totalScenes, totalChunks, totalEmbeddings, chapterLength);
    }

    private void publishChapterEventsResolvedEvent(UUID jobId, UUID chapterId, StepResult result) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish ChapterEventsResolvedEvent", chapterId);
            return;
        }

        UUID bookId = chapterOpt.get().getBookId();
        boolean processed = result.success();
        int mentionCount = result.counts().getOrDefault("rawMentionsProcessed", 0);
        int chapterEventCount = result.counts().getOrDefault("chapterEventsCreated", 0);
        int failedCorefWindowCount = result.counts().getOrDefault("failedCorefWindowCount", 0);

        ChapterEventsResolvedEvent event = new ChapterEventsResolvedEvent(
                this, jobId, chapterId, bookId, processed, mentionCount, chapterEventCount, failedCorefWindowCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published ChapterEventsResolvedEvent: jobId={}, chapterId={}, bookId={}, processed={}, mentions={}, events={}, failedCoref={}",
                jobId, chapterId, bookId, processed, mentionCount, chapterEventCount, failedCorefWindowCount);
    }

    // ── Chapter-scoped resolution events ──────────────────────────────────────

    private void publishChapterIndividualsResolvedEvent(UUID jobId, UUID chapterId, StepResult result) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish ChapterIndividualsResolvedEvent", chapterId);
            return;
        }

        UUID bookId = chapterOpt.get().getBookId();
        boolean processed = result.success();
        int mentionCount = result.counts().getOrDefault("rawIndividualsProcessed", 0);
        int chapterIndividualCount = result.counts().getOrDefault("chapterIndividualsCreated", 0);

        ChapterIndividualsResolvedEvent event = new ChapterIndividualsResolvedEvent(
                this, jobId, chapterId, bookId, processed, mentionCount, chapterIndividualCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published ChapterIndividualsResolvedEvent: jobId={}, chapterId={}, bookId={}, processed={}, mentions={}, individuals={}",
                jobId, chapterId, bookId, processed, mentionCount, chapterIndividualCount);
    }

    private void publishChapterCollectivesResolvedEvent(UUID jobId, UUID chapterId, StepResult result) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish ChapterCollectivesResolvedEvent", chapterId);
            return;
        }

        UUID bookId = chapterOpt.get().getBookId();
        boolean processed = result.success();
        int mentionCount = result.counts().getOrDefault("rawCollectivesProcessed", 0);
        int chapterCollectiveCount = result.counts().getOrDefault("chapterCollectivesCreated", 0);

        ChapterCollectivesResolvedEvent event = new ChapterCollectivesResolvedEvent(
                this, jobId, chapterId, bookId, processed, mentionCount, chapterCollectiveCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published ChapterCollectivesResolvedEvent: jobId={}, chapterId={}, bookId={}, processed={}, mentions={}, collectives={}",
                jobId, chapterId, bookId, processed, mentionCount, chapterCollectiveCount);
    }

    private void publishChapterLocationsResolvedEvent(UUID jobId, UUID chapterId, StepResult result) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish ChapterLocationsResolvedEvent", chapterId);
            return;
        }

        UUID bookId = chapterOpt.get().getBookId();
        boolean processed = result.success();
        int mentionCount = result.counts().getOrDefault("rawLocationsProcessed", 0);
        int chapterLocationCount = result.counts().getOrDefault("chapterLocationsCreated", 0);

        ChapterLocationsResolvedEvent event = new ChapterLocationsResolvedEvent(
                this, jobId, chapterId, bookId, processed, mentionCount, chapterLocationCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published ChapterLocationsResolvedEvent: jobId={}, chapterId={}, bookId={}, processed={}, mentions={}, locations={}",
                jobId, chapterId, bookId, processed, mentionCount, chapterLocationCount);
    }

    private void publishChapterObjectsResolvedEvent(UUID jobId, UUID chapterId, StepResult result) {
        Optional<Chapter> chapterOpt = chapterGraphRepository.findById(chapterId);
        if (chapterOpt.isEmpty()) {
            log.warn("[StepEventMapper] Chapter not found: {} — cannot publish ChapterObjectsResolvedEvent", chapterId);
            return;
        }

        UUID bookId = chapterOpt.get().getBookId();
        boolean processed = result.success();
        int mentionCount = result.counts().getOrDefault("rawObjectsProcessed", 0);
        int chapterObjectCount = result.counts().getOrDefault("chapterObjectsCreated", 0);

        ChapterObjectsResolvedEvent event = new ChapterObjectsResolvedEvent(
                this, jobId, chapterId, bookId, processed, mentionCount, chapterObjectCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published ChapterObjectsResolvedEvent: jobId={}, chapterId={}, bookId={}, processed={}, mentions={}, objects={}",
                jobId, chapterId, bookId, processed, mentionCount, chapterObjectCount);
    }

    // ── Book-scoped reduction events ──────────────────────────────────────────

    private void publishBookIndividualsReducedEvent(UUID jobId, UUID bookId, StepResult result) {
        boolean processed = result.success();
        int chapterIndividualCount = result.counts().getOrDefault("chapterIndividualsProcessed", 0);
        int bookIndividualCount = result.counts().getOrDefault("bookIndividualsCreated", 0);

        BookIndividualsReducedEvent event = new BookIndividualsReducedEvent(
                this, jobId, null, bookId, processed, chapterIndividualCount, bookIndividualCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published BookIndividualsReducedEvent: jobId={}, bookId={}, processed={}, chapterIndividuals={}, bookIndividuals={}",
                jobId, bookId, processed, chapterIndividualCount, bookIndividualCount);
    }

    private void publishBookCollectivesReducedEvent(UUID jobId, UUID bookId, StepResult result) {
        boolean processed = result.success();
        int chapterCollectiveCount = result.counts().getOrDefault("chapterCollectivesProcessed", 0);
        int bookCollectiveCount = result.counts().getOrDefault("bookCollectivesCreated", 0);

        BookCollectivesReducedEvent event = new BookCollectivesReducedEvent(
                this, jobId, null, bookId, processed, chapterCollectiveCount, bookCollectiveCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published BookCollectivesReducedEvent: jobId={}, bookId={}, processed={}, chapterCollectives={}, bookCollectives={}",
                jobId, bookId, processed, chapterCollectiveCount, bookCollectiveCount);
    }

    private void publishBookLocationsReducedEvent(UUID jobId, UUID bookId, StepResult result) {
        boolean processed = result.success();
        int chapterLocationCount = result.counts().getOrDefault("chapterLocationsProcessed", 0);
        int bookLocationCount = result.counts().getOrDefault("bookLocationsCreated", 0);

        BookLocationsReducedEvent event = new BookLocationsReducedEvent(
                this, jobId, null, bookId, processed, chapterLocationCount, bookLocationCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published BookLocationsReducedEvent: jobId={}, bookId={}, processed={}, chapterLocations={}, bookLocations={}",
                jobId, bookId, processed, chapterLocationCount, bookLocationCount);
    }

    private void publishBookObjectsReducedEvent(UUID jobId, UUID bookId, StepResult result) {
        boolean processed = result.success();
        int chapterObjectCount = result.counts().getOrDefault("chapterObjectsProcessed", 0);
        int bookObjectCount = result.counts().getOrDefault("bookObjectsCreated", 0);

        BookObjectsReducedEvent event = new BookObjectsReducedEvent(
                this, jobId, null, bookId, processed, chapterObjectCount, bookObjectCount);
        eventPublisher.publishEvent(event);

        log.info("[StepEventMapper] Published BookObjectsReducedEvent: jobId={}, bookId={}, processed={}, chapterObjects={}, bookObjects={}",
                jobId, bookId, processed, chapterObjectCount, bookObjectCount);
    }
}
