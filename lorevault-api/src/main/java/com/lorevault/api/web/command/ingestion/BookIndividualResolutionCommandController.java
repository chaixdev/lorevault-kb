package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.ingestion.BookIndividualReductionService;
import com.lorevault.api.support.BookIndividualResolutionResponse;
import com.lorevault.api.support.ErrorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/command/ingest")
@Tag(name = "Ingestion", description = "Content ingestion operations")
public class BookIndividualResolutionCommandController {

    private static final Logger log = LoggerFactory.getLogger(BookIndividualResolutionCommandController.class);

    private final BookGraphRepository bookGraphRepository;
    private final BookIndividualReductionService bookIndividualReductionService;

    public BookIndividualResolutionCommandController(
            BookGraphRepository bookGraphRepository,
            BookIndividualReductionService bookIndividualReductionService
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.bookIndividualReductionService = bookIndividualReductionService;
    }

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

        if (bookGraphRepository.findById(bookUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve book individuals: bookId={}", bookUuid);
        BookIndividualResolutionResponse response = bookIndividualReductionService.resolveBook(bookUuid);
        return ResponseEntity.ok(response);
    }
}
