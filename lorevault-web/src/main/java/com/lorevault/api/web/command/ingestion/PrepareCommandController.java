package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.submission.IngestionService;
import com.lorevault.api.ingestion.submission.IngestionSubmissionResult;
import com.lorevault.api.web.ErrorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CQRS command controller for preparing a chapter for step-by-step ingestion.
 *
 * <p>Creates a chapter and an ingestion job but does <em>not</em> publish
 * {@code ChapterIngestionEvent}. The caller drives individual pipeline steps
 * via the step execution endpoints.
 *
 * <p>Supports two content types:
 * <ul>
 *   <li>{@code application/json} — programmatic use (agents, scripts)</li>
 *   <li>{@code multipart/form-data} — file upload (same as the existing
 *       ingest endpoint but without triggering the pipeline)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/command/ingest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ingestion", description = "Content ingestion operations")
public class PrepareCommandController {

    private final IngestionService ingestionService;

    /**
     * Prepare a chapter for step-by-step ingestion (JSON body).
     *
     * <p>Creates the chapter (if new) and an ingestion job, but does not
     * publish {@code ChapterIngestionEvent}. The caller is responsible for
     * invoking individual pipeline steps via their step execution endpoints.
     */
    @PostMapping(value = "/prepare", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> prepareChapter(@Valid @RequestBody PrepareChapterRequest request) {
        log.info("[CMD] Prepare chapter: bookId={}, chapterNumber={}, title={}",
                request.getBookId(), request.getChapterNumber(), request.getChapterTitle());

        try {
            IngestionSubmissionResult result = ingestionService.prepareChapter(
                    request.getBookId(),
                    request.getChapterNumber(),
                    request.getChapterTitle(),
                    request.getChapterText()
            );

            log.info("[CMD] Prepared chapter: jobId={}, chapterId={}", result.jobId(), result.chapterId());

            return ResponseEntity.status(HttpStatus.CREATED).body(new PrepareChapterResponse(
                    result.jobId(),
                    result.chapterId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("PREPARE_FAILED")
                    .message(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/prepare")
                    .build());
        } catch (Exception e) {
            log.error("[CMD] Prepare chapter failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.builder()
                    .code("PREPARE_ERROR")
                    .message("Chapter preparation failed. Please try again.")
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/prepare")
                    .build());
        }
    }

    /**
     * Response record for the prepare endpoint.
     */
    public record PrepareChapterResponse(UUID jobId, UUID chapterId) {}
}