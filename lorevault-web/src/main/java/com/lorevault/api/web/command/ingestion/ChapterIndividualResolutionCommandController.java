package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.ChapterIndividualResolutionService;
import com.lorevault.api.support.ChapterIndividualResolutionResponse;
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
public class ChapterIndividualResolutionCommandController {

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;

    @PostMapping("/chapters/{chapterId}/resolve-individuals")
    public ResponseEntity<?> resolveChapterIndividuals(@PathVariable String chapterId) {
        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/resolve-individuals")
                    .build());
        }

        if (!chapterIndividualResolutionService.chapterExists(chapterUuid)) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve chapter individuals: chapterId={}", chapterUuid);
        ChapterIndividualResolutionResponse response = chapterIndividualResolutionService.resolveChapter(chapterUuid);
        return ResponseEntity.ok(response);
    }
}
