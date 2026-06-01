package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.pipeline.StepKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import com.lorevault.api.graph.concept.consolidation.book.BookConceptConsolidationOperation;
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
public class BookConceptConsolidationCommandController {

    private final BookConceptConsolidationOperation bookConceptConsolidationOperation;
    private final StepEventMapper stepEventMapper;
    private final BookGraphRepository bookGraphRepository;

    @PostMapping("/books/{bookId}/book-consolidate-concepts")
    public ResponseEntity<?> consolidateBookConcepts(
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
                    .path("/api/command/ingest/books/" + bookId + "/book-consolidate-concepts")
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
                        .path("/api/command/ingest/books/" + bookId + "/book-consolidate-concepts")
                        .build());
            }
        }

        if (bookGraphRepository.findById(bookUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Reduce book concepts: bookId={}, jobId={}, fireEvents={}", bookUuid, jobUuid, fireEvents);
        StepResult result = bookConceptConsolidationOperation.execute(jobUuid, bookUuid);

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.BOOK_CONSOLIDATE_CONCEPTS, "book", bookId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for BOOK_CONSOLIDATE_CONCEPTS: jobId={}, bookId={}", jobUuid, bookUuid);
            stepEventMapper.publishCompletionEvent(StepKey.BOOK_CONSOLIDATE_CONCEPTS, jobUuid, bookUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
