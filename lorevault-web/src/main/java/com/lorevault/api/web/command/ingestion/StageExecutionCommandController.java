package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.orchestration.scene.SceneDetectionHandler;
import com.lorevault.api.library.chunk.ChunkingHandler;
import com.lorevault.api.ai.embedding.EmbeddingHandler;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.graph.event.consolidation.chapter.ChapterEventConsolidationHandler;
import com.lorevault.api.orchestration.pipeline.StageResult;
import com.lorevault.api.web.ErrorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/command/ingest")
@Tag(name = "Ingestion", description = "Content ingestion operations")
@Slf4j
@RequiredArgsConstructor
public class StageExecutionCommandController {

    private final SceneDetectionHandler sceneDetectionHandler;
    private final ChunkingHandler chunkingHandler;
    private final EmbeddingHandler embeddingHandler;
    private final ChapterEventConsolidationHandler chapterEventConsolidator;
    private final StageEventMapper stepEventMapper;

    @PostMapping("/chapters/{chapterId}/detect-scenes")
    public ResponseEntity<?> detectScenes(
            @PathVariable String chapterId,
            @RequestParam(defaultValue = "false") boolean fireEvents,
            @RequestParam(required = false) String jobId) {

        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/detect-scenes")
                    .build());
        }

        UUID jobUuid = null;
        if (jobId != null) {
            try {
                jobUuid = UUID.fromString(jobId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(ErrorResponse.builder()
                        .code("INVALID_JOB_ID")
                        .message("Job ID must be a valid UUID")
                        .details("jobId", jobId)
                        .timestamp(LocalDateTime.now())
                        .path("/api/command/ingest/chapters/" + chapterId + "/detect-scenes")
                        .build());
            }
        }

        log.info("[CMD] Detect scenes: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StageResult result = sceneDetectionHandler.execute(new StageExecutionContext(null, jobUuid, chapterUuid, null, StageKey.SCENE_SEGMENTATION));

        StageExecutionResponse response = StageExecutionResponse.from(result, StageKey.SCENE_SEGMENTATION, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for DETECT_SCENES: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StageKey.SCENE_SEGMENTATION, jobUuid, chapterUuid, result);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/chapters/{chapterId}/chunk")
    public ResponseEntity<?> chunkChapter(
            @PathVariable String chapterId,
            @RequestParam(defaultValue = "false") boolean fireEvents,
            @RequestParam(required = false) String jobId) {

        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/chunk")
                    .build());
        }

        UUID jobUuid = null;
        if (jobId != null) {
            try {
                jobUuid = UUID.fromString(jobId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(ErrorResponse.builder()
                        .code("INVALID_JOB_ID")
                        .message("Job ID must be a valid UUID")
                        .details("jobId", jobId)
                        .timestamp(LocalDateTime.now())
                        .path("/api/command/ingest/chapters/" + chapterId + "/chunk")
                        .build());
            }
        }

        log.info("[CMD] Chunk chapter: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StageResult result = chunkingHandler.execute(new StageExecutionContext(null, jobUuid, chapterUuid, null, StageKey.CHUNKING));

        StageExecutionResponse response = StageExecutionResponse.from(result, StageKey.CHUNKING, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for CHUNK: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StageKey.CHUNKING, jobUuid, chapterUuid, result);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/chapters/{chapterId}/embed")
    public ResponseEntity<?> embedChapter(
            @PathVariable String chapterId,
            @RequestParam(defaultValue = "false") boolean fireEvents,
            @RequestParam(required = false) String jobId) {

        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/embed")
                    .build());
        }

        UUID jobUuid = null;
        if (jobId != null) {
            try {
                jobUuid = UUID.fromString(jobId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(ErrorResponse.builder()
                        .code("INVALID_JOB_ID")
                        .message("Job ID must be a valid UUID")
                        .details("jobId", jobId)
                        .timestamp(LocalDateTime.now())
                        .path("/api/command/ingest/chapters/" + chapterId + "/embed")
                        .build());
            }
        }

        log.info("[CMD] Embed chapter: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StageResult result = embeddingHandler.execute(new StageExecutionContext(null, jobUuid, chapterUuid, null, StageKey.EMBEDDING));

        StageExecutionResponse response = StageExecutionResponse.from(result, StageKey.EMBEDDING, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for EMBED: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StageKey.EMBEDDING, jobUuid, chapterUuid, result);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-events")
    public ResponseEntity<?> consolidateChapterEvents(
            @PathVariable String chapterId,
            @RequestParam(defaultValue = "false") boolean fireEvents,
            @RequestParam(required = false) String jobId) {

        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/chapter-consolidate-events")
                    .build());
        }

        UUID jobUuid = null;
        if (jobId != null) {
            try {
                jobUuid = UUID.fromString(jobId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(ErrorResponse.builder()
                        .code("INVALID_JOB_ID")
                        .message("Job ID must be a valid UUID")
                        .details("jobId", jobId)
                        .timestamp(LocalDateTime.now())
                        .path("/api/command/ingest/chapters/" + chapterId + "/chapter-consolidate-events")
                        .build());
            }
        }

        log.info("[CMD] Resolve chapter events: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StageResult result = chapterEventConsolidator.execute(
                new StageExecutionContext(null, jobUuid, chapterUuid, null, StageKey.CHAPTER_EVENT_CONSOLIDATION));

        StageExecutionResponse response = StageExecutionResponse.from(result, StageKey.CHAPTER_EVENT_CONSOLIDATION, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for CHAPTER_CONSOLIDATE_EVENTS: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StageKey.CHAPTER_EVENT_CONSOLIDATION, jobUuid, chapterUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
