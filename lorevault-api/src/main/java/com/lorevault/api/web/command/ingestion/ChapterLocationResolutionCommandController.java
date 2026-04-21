package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.ingestion.ChapterLocationResolutionService;
import com.lorevault.api.support.ChapterLocationResolutionResponse;
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
public class ChapterLocationResolutionCommandController {

    private final ChapterGraphRepository chapterGraphRepository;
    private final ChapterLocationResolutionService chapterLocationResolutionService;

    @PostMapping("/chapters/{chapterId}/resolve-locations")
    public ResponseEntity<?> resolveChapterLocations(@PathVariable String chapterId) {
        UUID chapterUuid;
        try {
            chapterUuid = UUID.fromString(chapterId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_CHAPTER_ID")
                    .message("Chapter ID must be a valid UUID")
                    .details("chapterId", chapterId)
                    .timestamp(LocalDateTime.now())
                    .path("/api/command/ingest/chapters/" + chapterId + "/resolve-locations")
                    .build());
        }

        if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve chapter locations: chapterId={}", chapterUuid);
        ChapterLocationResolutionResponse response = chapterLocationResolutionService.resolveChapter(chapterUuid);
        return ResponseEntity.ok(response);
    }
}
