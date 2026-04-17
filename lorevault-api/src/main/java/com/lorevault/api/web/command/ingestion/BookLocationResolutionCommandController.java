package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.ingestion.BookLocationReductionService;
import com.lorevault.api.support.BookLocationResolutionResponse;
import com.lorevault.api.support.ErrorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class BookLocationResolutionCommandController {

    private static final Logger log = LoggerFactory.getLogger(BookLocationResolutionCommandController.class);

    private final BookGraphRepository bookGraphRepository;
    private final BookLocationReductionService bookLocationReductionService;

    public BookLocationResolutionCommandController(
            BookGraphRepository bookGraphRepository,
            BookLocationReductionService bookLocationReductionService
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.bookLocationReductionService = bookLocationReductionService;
    }

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

        if (bookGraphRepository.findById(bookUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve book locations: bookId={}", bookUuid);
        BookLocationResolutionResponse response = bookLocationReductionService.resolveBook(bookUuid);
        return ResponseEntity.ok(response);
    }
}
