package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionResult;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionService;
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
public class ChapterCollectiveResolutionCommandController {

    private final ChapterCollectiveResolutionService chapterCollectiveResolutionService;

    @PostMapping("/chapters/{chapterId}/resolve-collectives")
    public ResponseEntity<?> resolveChapterCollectives(@PathVariable String chapterId) {
        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/resolve-collectives")
                    .build());
        }

        if (!chapterCollectiveResolutionService.chapterExists(chapterUuid)) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve chapter collectives: chapterId={}", chapterUuid);
        ChapterCollectiveResolutionResult result = chapterCollectiveResolutionService.resolveChapter(chapterUuid);
        ChapterCollectiveResolutionResponse response = new ChapterCollectiveResolutionResponse(
                result.chapterId(),
                result.success(),
                result.rawCollectivesProcessed(),
                result.chapterCollectivesCreated(),
                result.message()
        );
        return ResponseEntity.ok(response);
    }
}
