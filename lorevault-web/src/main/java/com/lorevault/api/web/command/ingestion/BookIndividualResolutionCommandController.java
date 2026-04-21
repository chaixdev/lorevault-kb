package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.BookIndividualReductionService;
import com.lorevault.api.support.BookIndividualResolutionResponse;
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
public class BookIndividualResolutionCommandController {

    private final BookIndividualReductionService bookIndividualReductionService;

    @PostMapping("/books/{bookId}/resolve-individuals")
    public ResponseEntity<?> resolveBookIndividuals(@PathVariable String bookId) {
        UUID bookUuid;
        try {
            bookUuid = UUID.fromString(bookId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_BOOK_ID")
                    .message("Book ID must be a valid UUID")
                    .details("bookId", bookId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/books/" + bookId + "/resolve-individuals")
                    .build());
        }

        if (!bookIndividualReductionService.bookExists(bookUuid)) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve book individuals: bookId={}", bookUuid);
        BookIndividualResolutionResponse response = bookIndividualReductionService.resolveBook(bookUuid);
        return ResponseEntity.ok(response);
    }
}
