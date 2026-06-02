package com.lorevault.api.web.query.step;

import com.lorevault.api.orchestration.pipeline.StageKey;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for pipeline stage discoverability (Query operations).
 *
 * <p>Exposes metadata about available ingestion pipeline stages, their scope
 * (chapter vs. book), and prerequisite relationships.
 */
@RestController
@RequestMapping("/api/query/ingestion")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ingestion Steps", description = "Pipeline step discoverability")
public class StageQueryController {

    /**
     * Description and scope for each queryable stage.
     */
    private static final List<StageInfo> STAGE_INFOS = List.of(
            new StageInfo("scene-segmentation", "Detect semantic scene boundaries in chapter text", "chapter", List.of()),
            new StageInfo("chunking", "Split detected scenes into text chunks for embedding", "chapter", List.of("scene-segmentation")),
            new StageInfo("embedding", "Generate vector embeddings for scene chunks", "chapter", List.of("chunking")),
            new StageInfo("chapter-consolidate-individuals", "Consolidate individual entity mentions across scenes", "chapter", List.of("scene-segmentation")),
            new StageInfo("chapter-consolidate-collectives", "Consolidate collective entity mentions across scenes", "chapter", List.of("scene-segmentation")),
            new StageInfo("chapter-consolidate-locations", "Consolidate location entity mentions across scenes", "chapter", List.of("scene-segmentation")),
            new StageInfo("chapter-consolidate-objects", "Consolidate object entity mentions across scenes", "chapter", List.of("scene-segmentation")),
            new StageInfo("chapter-consolidate-events", "Consolidate narrative events across scenes", "chapter", List.of("scene-segmentation")),
            new StageInfo("chapter-event-embedding", "Embed narrative events for search and matching", "chapter", List.of("chapter-consolidate-events")),
            new StageInfo("book-consolidate-individuals", "Consolidate chapter-level individuals to book-level entities", "book", List.of("chapter-consolidate-individuals")),
            new StageInfo("book-consolidate-collectives", "Consolidate chapter-level collectives to book-level entities", "book", List.of("chapter-consolidate-collectives")),
            new StageInfo("book-consolidate-locations", "Consolidate chapter-level locations to book-level entities", "book", List.of("chapter-consolidate-locations")),
            new StageInfo("book-consolidate-objects", "Consolidate chapter-level objects to book-level entities", "book", List.of("chapter-consolidate-objects")),
            new StageInfo("book-event-candidate-generation", "Generate candidate narrative events at book scope", "book", List.of("chapter-event-embedding"))
    );

    /**
     * Returns all registered pipeline step definitions in pipeline order.
     */
    @GetMapping("/steps")
    public ResponseEntity<?> getSteps() {
        log.debug("Step definition list requested");
        StepsResponse response = new StepsResponse(STAGE_INFOS);
        log.debug("Returning {} step definitions", STAGE_INFOS.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Response DTO wrapping a list of step definitions.
     */
    record StepsResponse(List<StageInfo> steps) {}

    /**
     * Response DTO for a single step definition.
     *
     * @param key           kebab-case URL segment identifying the step
     * @param description   human-readable description of what the step does
     * @param scope         {@code "chapter"} or {@code "book"}
     * @param prerequisites kebab-case URL segments of prerequisite steps
     */
    record StageInfo(
            String key,
            String description,
            String scope,
            List<String> prerequisites
    ) {}
}
