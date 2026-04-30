package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.resolution.object.BookObjectReductionService;
import com.lorevault.api.ingestion.resolution.object.BookObjectResolutionResult;
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
public class BookObjectResolutionCommandController {

    private final BookObjectReductionService bookObjectReductionService;

    @PostMapping("/books/{bookId}/resolve-objects")
    public ResponseEntity<?> resolveBookObjects(@PathVariable String bookId) {
        UUID bookUuid;
        try {
            bookUuid = UUID.fromString(bookId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_BOOK_ID")
                    .message("Book ID must be a valid UUID")
                    .details("bookId", bookId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/books/" + bookId + "/resolve-objects")
                    .build());
        }

        if (!bookObjectReductionService.bookExists(bookUuid)) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve book objects: bookId={}", bookUuid);
        BookObjectResolutionResult result = bookObjectReductionService.resolveBook(bookUuid);
        BookObjectResolutionResponse response = new BookObjectResolutionResponse(
                result.bookId(),
                result.success(),
                result.chapterObjectsProcessed(),
                result.bookObjectsCreated(),
                result.message()
        );
        return ResponseEntity.ok(response);
    }
}
