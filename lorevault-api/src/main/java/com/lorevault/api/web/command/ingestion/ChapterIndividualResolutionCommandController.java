package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.ingestion.ChapterIndividualResolutionService;
import com.lorevault.api.support.ChapterIndividualResolutionResponse;
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
public class ChapterIndividualResolutionCommandController {

    private static final Logger log = LoggerFactory.getLogger(ChapterIndividualResolutionCommandController.class);

    private final ChapterGraphRepository chapterGraphRepository;
    private final ChapterIndividualResolutionService chapterIndividualResolutionService;

    public ChapterIndividualResolutionCommandController(
            ChapterGraphRepository chapterGraphRepository,
            ChapterIndividualResolutionService chapterIndividualResolutionService
    ) {
        this.chapterGraphRepository = chapterGraphRepository;
        this.chapterIndividualResolutionService = chapterIndividualResolutionService;
    }

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

        if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        log.info("[CMD] Resolve chapter individuals: chapterId={}", chapterUuid);
        ChapterIndividualResolutionResponse response = chapterIndividualResolutionService.resolveChapter(chapterUuid);
        return ResponseEntity.ok(response);
    }
}
