package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.application.resolution.BookLocationReductionService;
import com.lorevault.api.ingestion.application.result.BookLocationResolutionResult;
import com.lorevault.api.web.command.ingestion.BookLocationResolutionResponse;
import com.lorevault.api.web.ErrorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/command/ingest")
@Tag(name = "Ingestion", description = "Content ingestion operations")
@Slf4j
@RequiredArgsConstructor
public class BookLocationResolutionCommandController {

    private final BookLocationReductionService bookLocationReductionService;

    @PostMapping("/books/{bookId}/resolve-locations")
    public ResponseEntity<?> resolveBookLocations(@PathVariable String bookId) {
        UUID bookUuid;
        try {
            bookUuid = UUID.fromString(bookId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_BOOK_ID")
                    .message("Book ID must be a valid UUID")
                    .details("bookId", bookId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/books/" + bookId + "/resolve-locations")
                    .build());
        }

        if (!bookLocationReductionService.bookExists(bookUuid)) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve book locations: bookId={}", bookUuid);
        BookLocationResolutionResult result = bookLocationReductionService.resolveBook(bookUuid);
        BookLocationResolutionResponse response = new BookLocationResolutionResponse(
            result.bookId(),
            result.success(),
            result.chapterLocationsProcessed(),
            result.bookLocationsCreated(),
            result.message()
        );
        return ResponseEntity.ok(response);
    }
}
