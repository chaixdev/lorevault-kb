package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.resolution.event.ChapterEventAnnRerunResult;
import com.lorevault.api.ingestion.resolution.event.ChapterEventAnnRerunService;
import com.lorevault.api.web.ErrorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/command/ingest")
@Tag(name = "Ingestion", description = "Content ingestion operations")
public class EventAnnRerunCommandController {

    private static final Logger log = LoggerFactory.getLogger(EventAnnRerunCommandController.class);
    private static final String ENDPOINT_PATH = "/api/command/ingest/events/rerun-ann";

    private final ChapterEventAnnRerunService chapterEventAnnRerunService;

    public EventAnnRerunCommandController(ChapterEventAnnRerunService chapterEventAnnRerunService) {
        this.chapterEventAnnRerunService = chapterEventAnnRerunService;
    }

    @PostMapping("/events/rerun-ann")
    public ResponseEntity<?> rerunAnn(
            @RequestParam(required = false) String universeId,
            @RequestParam(required = false) String bookId,
            @RequestParam(required = false) String chapterId
    ) {
        try {
            UUID universeUuid = parseOptionalUuid(universeId, "universeId", "INVALID_UNIVERSE_ID");
            UUID bookUuid = parseOptionalUuid(bookId, "bookId", "INVALID_BOOK_ID");
            UUID chapterUuid = parseOptionalUuid(chapterId, "chapterId", "INVALID_CHAPTER_ID");

            if (universeUuid == null && bookUuid == null && chapterUuid == null) {
                return ResponseEntity.badRequest().body(ErrorResponse.builder()
                        .code("MISSING_UNIVERSE_ID")
                        .message("universeId is required when chapterId and bookId are not supplied")
                        .timestamp(LocalDateTime.now())
                        .path(ENDPOINT_PATH)
                        .build());
            }

            ChapterEventAnnRerunResult result = chapterEventAnnRerunService.rerun(universeUuid, bookUuid, chapterUuid);
            log.info("[CMD] Rerun event ANN pass: universeId={}, bookId={}, chapterId={}, triggeredChapterCount={}, jobId={}, correlationId={}",
                    universeUuid, bookUuid, chapterUuid, result.triggeredChapterCount(), result.jobId(), result.correlationId());
            return ResponseEntity.ok(result);
        } catch (InvalidUuidException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code(e.errorCode())
                    .message(e.getMessage())
                    .details(e.fieldName(), e.rawValue())
                    .timestamp(LocalDateTime.now())
                    .path(ENDPOINT_PATH)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("MISSING_UNIVERSE_ID")
                    .message(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .path(ENDPOINT_PATH)
                    .build());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UUID parseOptionalUuid(String value, String fieldName, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidUuidException(errorCode, fieldName, value);
        }
    }

    private static final class InvalidUuidException extends RuntimeException {
        private final String errorCode;
        private final String fieldName;
        private final String rawValue;

        private InvalidUuidException(String errorCode, String fieldName, String rawValue) {
            super(fieldName + " must be a valid UUID");
            this.errorCode = errorCode;
            this.fieldName = fieldName;
            this.rawValue = rawValue;
        }

        private String errorCode() {
            return errorCode;
        }

        private String fieldName() {
            return fieldName;
        }

        private String rawValue() {
            return rawValue;
        }
    }
}
