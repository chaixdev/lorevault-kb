package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.orchestration.scene.SceneDetectionOperation;
import com.lorevault.api.library.chunk.ChunkingOperation;
import com.lorevault.api.ai.embedding.EmbeddingOperation;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
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
public class StepExecutionCommandController {

    private final ChapterGraphRepository chapterGraphRepository;
    private final SceneDetectionOperation sceneDetectionOperation;
    private final ChunkingOperation chunkingOperation;
    private final EmbeddingOperation embeddingOperation;
    private final StageOperation chapterEventResolutionOperation;
    private final StepEventMapper stepEventMapper;

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

        if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Detect scenes: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StepResult result = sceneDetectionOperation.execute(jobUuid, chapterUuid);

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.DETECT_SCENES, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for DETECT_SCENES: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StepKey.DETECT_SCENES, jobUuid, chapterUuid, result);
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

        if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Chunk chapter: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StepResult result = chunkingOperation.execute(jobUuid, chapterUuid);

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.CHUNK, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for CHUNK: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StepKey.CHUNK, jobUuid, chapterUuid, result);
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

        if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Embed chapter: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StepResult result = embeddingOperation.execute(jobUuid, chapterUuid);

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.EMBED, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for EMBED: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StepKey.EMBED, jobUuid, chapterUuid, result);
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

        if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve chapter events: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StepResult result = chapterEventResolutionOperation.execute(
                new StageExecutionContext(null, jobUuid, chapterUuid, null, StageKey.CHAPTER_EVENT_CONSOLIDATION));

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.CHAPTER_CONSOLIDATE_EVENTS, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for CHAPTER_CONSOLIDATE_EVENTS: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StepKey.CHAPTER_CONSOLIDATE_EVENTS, jobUuid, chapterUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
