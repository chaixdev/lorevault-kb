package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.graph.collective.consolidation.book.BookCollectiveConsolidationHandler;
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
public class BookCollectiveConsolidationCommandController {

    private final BookCollectiveConsolidationHandler bookCollectiveConsolidator;
    private final StepEventMapper stepEventMapper;

    @PostMapping("/books/{bookId}/book-consolidate-collectives")
    public ResponseEntity<?> consolidateBookCollectives(
            @PathVariable String bookId,
            @RequestParam(defaultValue = "false") boolean fireEvents,
            @RequestParam(required = false) String jobId) {
        UUID bookUuid;
        try {
            bookUuid = UUID.fromString(bookId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_BOOK_ID")
                    .message("Book ID must be a valid UUID")
                    .details("bookId", bookId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/books/" + bookId + "/book-consolidate-collectives")
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
                        .path("/api/command/ingest/books/" + bookId + "/book-consolidate-collectives")
                        .build());
            }
        }

        log.info("[CMD] Reduce book collectives: bookId={}, jobId={}, fireEvents={}", bookUuid, jobUuid, fireEvents);
        StageResult result = bookCollectiveConsolidator.execute(
                new StageExecutionContext(null, jobUuid, null, bookUuid, StageKey.BOOK_COLLECTIVE_CONSOLIDATION));

        StageExecutionResponse response = StageExecutionResponse.from(result, StageKey.BOOK_COLLECTIVE_CONSOLIDATION, "book", bookId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for BOOK_CONSOLIDATE_COLLECTIVES: jobId={}, bookId={}", jobUuid, bookUuid);
            stepEventMapper.publishCompletionEvent(StageKey.BOOK_COLLECTIVE_CONSOLIDATION, jobUuid, bookUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
