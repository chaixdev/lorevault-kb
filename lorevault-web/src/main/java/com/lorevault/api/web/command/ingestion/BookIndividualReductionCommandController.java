package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.pipeline.StepKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualReductionOperation;
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
public class BookIndividualReductionCommandController {

    private final BookIndividualReductionOperation bookIndividualReductionOperation;
    private final StepEventMapper stepEventMapper;
    private final BookGraphRepository bookGraphRepository;

    @PostMapping("/books/{bookId}/reduce-individuals")
    public ResponseEntity<?> reduceBookIndividuals(
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
                    .path("/api/command/ingest/books/" + bookId + "/reduce-individuals")
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
                        .path("/api/command/ingest/books/" + bookId + "/reduce-individuals")
                        .build());
            }
        }

        if (bookGraphRepository.findById(bookUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Reduce book individuals: bookId={}, jobId={}, fireEvents={}", bookUuid, jobUuid, fireEvents);
        StepResult result = bookIndividualReductionOperation.execute(jobUuid, bookUuid);

        StepExecutionResponse response = StepExecutionResponse.from(result, StepKey.REDUCE_INDIVIDUALS, "book", bookId);

        if (fireEvents && result.success()) {
            log.info("[CMD] Publishing completion event for REDUCE_INDIVIDUALS: jobId={}, bookId={}", jobUuid, bookUuid);
            stepEventMapper.publishCompletionEvent(StepKey.REDUCE_INDIVIDUALS, jobUuid, bookUuid, result);
        }

        return ResponseEntity.ok(response);
    }
}
