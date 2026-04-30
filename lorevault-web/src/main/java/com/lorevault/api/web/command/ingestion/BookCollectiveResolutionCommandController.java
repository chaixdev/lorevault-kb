package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.resolution.collective.BookCollectiveReductionService;
import com.lorevault.api.ingestion.resolution.collective.BookCollectiveResolutionResult;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/command/ingest")
@Tag(name = "Ingestion", description = "Content ingestion operations")
@Slf4j
@RequiredArgsConstructor
public class BookCollectiveResolutionCommandController {

    private final BookCollectiveReductionService bookCollectiveReductionService;

    @PostMapping("/books/{bookId}/resolve-collectives")
    public ResponseEntity<?> resolveBookCollectives(@PathVariable String bookId) {
        UUID bookUuid;
        try {
            bookUuid = UUID.fromString(bookId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_BOOK_ID")
                    .message("Book ID must be a valid UUID")
                    .details("bookId", bookId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/books/" + bookId + "/resolve-collectives")
                    .build());
        }

        if (!bookCollectiveReductionService.bookExists(bookUuid)) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve book collectives: bookId={}", bookUuid);
        BookCollectiveResolutionResult result = bookCollectiveReductionService.resolveBook(bookUuid);
        BookCollectiveResolutionResponse response = new BookCollectiveResolutionResponse(
                result.bookId(),
                result.success(),
                result.chapterCollectivesProcessed(),
                result.bookCollectivesCreated(),
                result.message()
        );
        return ResponseEntity.ok(response);
    }
}
