package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.resolution.object.ChapterObjectResolutionResult;
import com.lorevault.api.ingestion.resolution.object.ChapterObjectResolutionService;
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
public class ChapterObjectResolutionCommandController {

    private final ChapterObjectResolutionService chapterObjectResolutionService;

    @PostMapping("/chapters/{chapterId}/resolve-objects")
    public ResponseEntity<?> resolveChapterObjects(@PathVariable String chapterId) {
        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/resolve-objects")
                    .build());
        }

        if (!chapterObjectResolutionService.chapterExists(chapterUuid)) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve chapter objects: chapterId={}", chapterUuid);
        ChapterObjectResolutionResult result = chapterObjectResolutionService.resolveChapter(chapterUuid);
        ChapterObjectResolutionResponse response = new ChapterObjectResolutionResponse(
                result.chapterId(),
                result.success(),
                result.rawObjectsProcessed(),
                result.chapterObjectsCreated(),
                result.message()
        );
        return ResponseEntity.ok(response);
    }
}
