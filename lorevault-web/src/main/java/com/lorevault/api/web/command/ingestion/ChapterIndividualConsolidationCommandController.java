package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationHandler;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
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
public class ChapterIndividualConsolidationCommandController {

    private final ChapterIndividualConsolidationHandler chapterIndividualConsolidator;
    private final StageEventMapper stepEventMapper;

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-individuals")
    public ResponseEntity<?> consolidateChapterIndividuals(
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
                    .path("/api/command/ingest/chapters/" + chapterId + "/chapter-consolidate-individuals")
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
                        .path("/api/command/ingest/chapters/" + chapterId + "/chapter-consolidate-individuals")
                        .build());
            }
        }

        log.info("[CMD] Resolve chapter individuals: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StageResult result = chapterIndividualConsolidator.execute(
                new StageExecutionContext(null, jobUuid, chapterUuid, null, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION));

        StageExecutionResponse response = StageExecutionResponse.from(result, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for CHAPTER_CONSOLIDATE_INDIVIDUALS: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, jobUuid, chapterUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
