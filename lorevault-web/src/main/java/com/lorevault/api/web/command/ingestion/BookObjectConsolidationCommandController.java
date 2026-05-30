package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.pipeline.StepKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import com.lorevault.api.graph.object.consolidation.book.BookObjectConsolidationOperation;
import com.lorevault.api.library.book.BookGraphRepository;
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
public class BookObjectConsolidationCommandController {

    private final BookObjectConsolidationOperation bookObjectReductionOperation;
    private final StepEventMapper stepEventMapper;
    private final BookGraphRepository bookGraphRepository;

    @PostMapping("/books/{bookId}/book-consolidate-objects")
    public ResponseEntity<?> consolidateBookObjects(
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
                    .path("/api/command/ingest/books/" + bookId + "/book-consolidate-objects")
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
                        .path("/api/command/ingest/books/" + bookId + "/book-consolidate-objects")
                        .build());
            }
        }

        if (bookGraphRepository.findById(bookUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Reduce book objects: bookId={}, jobId={}, fireEvents={}", bookUuid, jobUuid, fireEvents);
        StepResult result = bookObjectReductionOperation.execute(jobUuid, bookUuid);

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.BOOK_CONSOLIDATE_OBJECTS, "book", bookId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for BOOK_CONSOLIDATE_OBJECTS: jobId={}, bookId={}", jobUuid, bookUuid);
            stepEventMapper.publishCompletionEvent(StepKey.BOOK_CONSOLIDATE_OBJECTS, jobUuid, bookUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
