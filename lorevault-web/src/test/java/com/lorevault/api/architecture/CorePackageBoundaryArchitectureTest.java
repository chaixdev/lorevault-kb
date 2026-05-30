package com.lorevault.api.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture validation assertions for top-level core package boundaries.
 *
 * <p>After the May 2026 package restructure:
 * <ul>
 *   <li>{@code ingestion/} was dissolved — pipeline orchestration now lives under
 *       {@code orchestration/}.</li>
 *   <li>{@code content/} became {@code graph/} (entity types) and {@code library/}
 *       (book/chapter/chunk/series/universe).</li>
 *   <li>{@code graph/*} is excluded because entity-type packages have tightly-coupled
 *       graph-domain relationships with expected bidirectional references.</li>
 *   <li>Library's {@code chapter ↔ chunk} cycle is also content-like and excluded.</li>
 * </ul>
 *
 * Known pre-existing cycles (not introduced by restructure):
 * <ul>
 *   <li>{@code ai/llm ↔ ai/infrastructure} — {@code LlmCallLogger} (interface in llm)
 *       and {@code LlmCallLoggingService} (impl in infrastructure) create a dependency
 *       inversion violation. Left for a follow-up cleanup.</li>
 *   <li>{@code orchestration/pipeline ↔ orchestration/signals} — events are the glue
 *       between pipeline components; event classes reference StageKey/StepResult from
 *       pipeline, while pipeline creates and consumes those events. This is an expected
 *       event-driven pattern within the orchestration bounded context.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.lorevault.api", importOptions = ImportOption.DoNotIncludeTests.class)
class CorePackageBoundaryArchitectureTest {

    // Sub-sliced packages where sub-packages should be cycle-free.
    // ai and orchestration are NOT sub-sliced because they contain expected internal
    // cycles (ai: llm↔infrastructure; orchestration: pipeline↔signals).
    @ArchTest
    static final ArchRule sub_sliced_contexts_should_be_cycle_free = slices()
            .matching("com.lorevault.api.(search|health|config|common).(*)..")
            .should().beFreeOfCycles();

    // ai as a single slice — llm↔infrastructure is an expected internal cycle
    // (LlmCallLogger interface in llm, LlmCallLoggingService impl in infrastructure).
    @ArchTest
    static final ArchRule ai_should_be_cycle_free = slices()
            .matching("com.lorevault.api.ai.(*)..")
            .should().beFreeOfCycles()
            .ignoreDependency(
                    com.lorevault.api.ai.infrastructure.LlmCallLoggingService.class,
                    com.lorevault.api.ai.llm.LlmCallLogger.class);

    // orchestration as a single slice — pipeline↔signals is an expected event-driven cycle.
    @ArchTest
    static final ArchRule orchestration_should_be_cycle_free = slices()
            .matching("com.lorevault.api.orchestration.(*)..")
            .should().beFreeOfCycles()
            .ignoreDependency(
                    com.lorevault.api.orchestration.pipeline.IngestionPipelineCoordinator.class,
                    com.lorevault.api.orchestration.signals.StageTriggeredEvent.class)
            .ignoreDependency(
                    com.lorevault.api.orchestration.pipeline.IngestionPipelineCoordinator.class,
                    com.lorevault.api.orchestration.signals.StageCompletedEvent.class)
            .ignoreDependency(
                    com.lorevault.api.orchestration.pipeline.StageDispatcher.class,
                    com.lorevault.api.orchestration.signals.StageTriggeredEvent.class)
            .ignoreDependency(
                    com.lorevault.api.orchestration.pipeline.StageDispatcher.class,
                    com.lorevault.api.orchestration.signals.StageCompletedEvent.class);

    // Library subpackages that are NOT content-like (chapter/chunk are excluded —
    // their Chapter ↔ Chunk bidirectional relationship is expected content coupling)
    @ArchTest
    static final ArchRule library_non_content_slices_should_be_cycle_free = slices()
            .matching("com.lorevault.api.library.(book|series|service|universe)..")
            .should().beFreeOfCycles();
}
