package com.lorevault.api.web.query.step;

import com.lorevault.api.orchestration.pipeline.StepCatalog;
import com.lorevault.api.orchestration.pipeline.StepDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for pipeline step discoverability (Query operations).
 *
 * <p>Exposes metadata about available ingestion pipeline steps, their scope
 * (chapter vs. book), and prerequisite relationships.
 */
@RestController
@RequestMapping("/api/query/ingestion")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ingestion Steps", description = "Pipeline step discoverability")
public class StepQueryController {

    private final StepCatalog stepCatalog;

    /**
     * Returns all registered pipeline step definitions in pipeline order.
     */
    @GetMapping("/steps")
    public ResponseEntity<?> getSteps() {
        log.debug("Step definition list requested");

        List<StepDefinition> definitions = stepCatalog.all();

        List<StepDefinitionResponse> stepResponses = definitions.stream()
                .map(def -> new StepDefinitionResponse(
                        def.key().toUrlSegment(),
                        def.description(),
                        def.scope(),
                        def.prerequisites().stream()
                                .map(pk -> pk.toUrlSegment())
                                .toList()
                ))
                .toList();

        StepsResponse response = new StepsResponse(stepResponses);

        log.debug("Returning {} step definitions", stepResponses.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Response DTO wrapping a list of step definitions.
     */
    record StepsResponse(List<StepDefinitionResponse> steps) {}

    /**
     * Response DTO for a single step definition.
     *
     * @param key           kebab-case URL segment identifying the step
     * @param description   human-readable description of what the step does
     * @param scope         {@code "chapter"} or {@code "book"}
     * @param prerequisites kebab-case URL segments of prerequisite steps
     */
    record StepDefinitionResponse(
            String key,
            String description,
            String scope,
            List<String> prerequisites
    ) {}
}
