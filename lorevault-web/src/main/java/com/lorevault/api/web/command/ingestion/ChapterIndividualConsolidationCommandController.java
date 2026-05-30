package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.orchestration.pipeline.StepKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationOperation;
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

    private final ChapterIndividualConsolidationOperation chapterIndividualResolutionOperation;
    private final StepEventMapper stepEventMapper;
    private final ChapterGraphRepository chapterGraphRepository;

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

        if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve chapter individuals: chapterId={}, jobId={}, fireEvents={}", chapterUuid, jobUuid, fireEvents);
        StepResult result = chapterIndividualResolutionOperation.execute(jobUuid, chapterUuid);

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.CHAPTER_CONSOLIDATE_INDIVIDUALS, "chapter", chapterId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for CHAPTER_CONSOLIDATE_INDIVIDUALS: jobId={}, chapterId={}", jobUuid, chapterUuid);
            stepEventMapper.publishCompletionEvent(StepKey.CHAPTER_CONSOLIDATE_INDIVIDUALS, jobUuid, chapterUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
