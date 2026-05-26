package com.lorevault.api.ingestion.triad;

import com.lorevault.api.ai.infrastructure.PromptName;
import com.lorevault.api.ai.llm.LlmClient;
import com.lorevault.api.ai.infrastructure.PromptRepository;
import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.job.IngestionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orchestrates triad-based scene analysis end-to-end, fully in-memory.
 */
@Service
public class SceneRelationshipAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(SceneRelationshipAnalysisService.class);

    private static final Set<String> ALLOWED_TRIAD_RELATIONS = Set.of(
            "R:temporal.before",
            "R:temporal.after",
            "R:temporal.overlaps",
            "R:temporal.contains",
            "R:temporal.during"
    );

    private static final int MAX_SEMANTIC_TRIAD_ATTEMPTS = 2;

    private static final Set<String> RETRYABLE_TRIAD_ANALYSIS_FAILURE_CODES = Set.of(
            "TRIAD_RESPONSE_MISSING",
            "TRIAD_RELATION_MISSING",
            "TRIAD_RELATION_TYPE_MISSING",
            "TRIAD_RELATION_CERTAINTY_MISSING",
            "TRIAD_RELATION_TYPE_INVALID"
    );

    public record TriadRelation(String temporalType, String certainty, String evidence) {}

    public record TriadIndividualExtraction(
            List<String> aliases,
            String physicalProperties,
            String age,
            String activity
    ) {}

    public record TriadLocationExtraction(
            String primaryName,
            List<String> aliases,
            String kind,
            String region,
            String description
    ) {}

    public record TriadObjectExtraction(
            List<String> aliases,
            String type,
            String material,
            String purpose,
            String description
    ) {}

    public record TriadCollectiveExtraction(
            List<String> aliases,
            String collectiveType,
            String certainty,
            String evidence
    ) {}

    public record TriadEventExtraction(
            String name,
            String eventType,
            String description,
            String temporalType,
            String certainty,
            String evidence
    ) {}

    public record TriadRelationClaimExtraction(
            String subject,
            String relationName,
            String relationDescription,
            String object,
            String certainty,
            String evidence
    ) {}

    public record TriadCurrentSceneEntities(
            List<TriadIndividualExtraction> individuals,
            List<TriadCollectiveExtraction> collectives,
            List<TriadObjectExtraction> objects,
            List<TriadLocationExtraction> locations,
            List<TriadEventExtraction> events,
            List<TriadRelationClaimExtraction> relations
    ) {
        public TriadCurrentSceneEntities(
                List<TriadIndividualExtraction> individuals,
                List<TriadLocationExtraction> locations
        ) {
            this(individuals, List.of(), List.of(), locations, List.of(), List.of());
        }

        public TriadCurrentSceneEntities(
                List<TriadIndividualExtraction> individuals,
                List<TriadObjectExtraction> objects,
                List<TriadLocationExtraction> locations
        ) {
            this(individuals, List.of(), objects, locations, List.of(), List.of());
        }
    }

    public record TriadStructuredResult(
            String timelineMarker,
            TriadRelation previousToCurrent,
            TriadRelation currentToNext,
            TriadCurrentSceneEntities currentSceneEntities
    ) {
        public TriadStructuredResult(String timelineMarker,
                                     TriadRelation previousToCurrent,
                                     TriadRelation currentToNext) {
            this(timelineMarker, previousToCurrent, currentToNext, null);
        }
    }

    private final TriadBuilderService triadBuilder;
    private final LlmClient llmClient;
    private final PromptRepository promptRepository;

    public SceneRelationshipAnalysisService(TriadBuilderService triadBuilder,
                                            LlmClient llmClient,
                                            PromptRepository promptRepository) {
        this.triadBuilder = triadBuilder;
        this.llmClient = llmClient;
        this.promptRepository = promptRepository;
    }

    /**
     * Analyze scene triads and return normalized results.
     */
    public TriadAnalysisModels.SceneRelationshipOutcome analyzeChapterTriads(UUID jobId, Chapter chapter) {
        return analyzeChapterTriads(jobId, chapter, ignored -> {
        });
    }

    public TriadAnalysisModels.SceneRelationshipOutcome analyzeChapterTriads(UUID jobId,
                                                                                             Chapter chapter,
                                                                                             Consumer<Map<String, Object>> onTriadStart) {
        List<TriadBuilderService.SceneTriad> triads = triadBuilder.buildTriadsForChapter(chapter);
        if (triads.isEmpty()) {
            return new TriadAnalysisModels.SceneRelationshipOutcome(List.of(), List.of(), List.of());
        }

        PromptTemplate systemTemplate = promptRepository.get(PromptName.SCENE_ANALYSIS);
        String systemPrompt = systemTemplate.render(Map.of());

        List<TriadAnalysisModels.SceneRelationshipAnalysis> analyses = new ArrayList<>();
        Map<Integer, List<TriadAnalysisModels.IndividualExtraction>> extractedIndividualsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.CollectiveExtraction>> extractedCollectivesBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.ObjectExtraction>> extractedObjectsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.LocationExtraction>> extractedLocationsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.EventExtraction>> extractedEventsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.RelationClaimExtraction>> extractedRelationClaimsBySceneIndex = new HashMap<>();

        int triadIndex = 0;
        for (TriadBuilderService.SceneTriad t : triads) {
            Map<String, Object> vars = buildUserVars(chapter, t);

            Map<String, Object> statusProps = new HashMap<>();
            statusProps.put("triadIndex", triadIndex++);
            statusProps.put("prevSceneId", t.previous() != null ? t.previous().getEventId() : null);
            statusProps.put("currentSceneId", t.current() != null ? t.current().getEventId() : null);
            statusProps.put("nextSceneId", t.next() != null ? t.next().getEventId() : null);
            statusProps.put("prevSceneIndex", t.previous() != null ? t.previous().getSceneIndex() : null);
            statusProps.put("currentSceneIndex", t.current().getSceneIndex());
            statusProps.put("nextSceneIndex", t.next() != null ? t.next().getSceneIndex() : null);

            onTriadStart.accept(new HashMap<>(statusProps));

            TriadStructuredResult normalized = analyzeTriadWithSemanticRetry(jobId, systemPrompt, vars, t, statusProps);

            String inv = normalized.previousToCurrent() != null
                    ? invertPrevToCurr(normalized.previousToCurrent().temporalType())
                    : null;

            analyses.add(new TriadAnalysisModels.SceneRelationshipAnalysis(
                    t.previous() != null ? t.previous().getEventId() : null,
                    t.current() != null ? t.current().getEventId() : null,
                    t.next() != null ? t.next().getEventId() : null,
                    t.previous() != null ? t.previous().getSceneIndex() : null,
                    t.current().getSceneIndex(),
                    t.next() != null ? t.next().getSceneIndex() : null,
                    normalized.timelineMarker(),
                    normalized.previousToCurrent() != null ? normalized.previousToCurrent().temporalType() : null,
                    normalized.previousToCurrent() != null ? normalized.previousToCurrent().certainty() : null,
                    normalized.previousToCurrent() != null ? normalized.previousToCurrent().evidence() : null,
                    normalized.currentToNext() != null ? normalized.currentToNext().temporalType() : null,
                    normalized.currentToNext() != null ? normalized.currentToNext().certainty() : null,
                    normalized.currentToNext() != null ? normalized.currentToNext().evidence() : null,
                    inv
            ));

            int sceneIndex = t.current().getSceneIndex() == null ? -1 : t.current().getSceneIndex();
            if (sceneIndex >= 0) {
                mergeIfNotEmpty(extractedIndividualsBySceneIndex, sceneIndex, normalizeIndividuals(normalized));
                mergeIfNotEmpty(extractedLocationsBySceneIndex, sceneIndex, normalizeLocations(normalized));
                mergeIfNotEmpty(extractedObjectsBySceneIndex, sceneIndex, normalizeObjects(normalized));
                mergeIfNotEmpty(extractedCollectivesBySceneIndex, sceneIndex, normalizeCollectives(normalized));
                mergeIfNotEmpty(extractedEventsBySceneIndex, sceneIndex, normalizeEvents(normalized));
                mergeIfNotEmpty(extractedRelationClaimsBySceneIndex, sceneIndex, normalizeRelationClaims(normalized));
            }
        }

        List<TriadAnalysisModels.SceneIndividualExtraction> sceneExtractions = coalesce(extractedIndividualsBySceneIndex, TriadAnalysisModels.SceneIndividualExtraction::new);
        List<TriadAnalysisModels.SceneLocationExtraction> sceneLocationExtractions = coalesce(extractedLocationsBySceneIndex, TriadAnalysisModels.SceneLocationExtraction::new);
        List<TriadAnalysisModels.SceneCollectiveExtraction> sceneCollectiveExtractions = coalesce(extractedCollectivesBySceneIndex, TriadAnalysisModels.SceneCollectiveExtraction::new);
        List<TriadAnalysisModels.SceneObjectExtraction> sceneObjectExtractions = coalesce(extractedObjectsBySceneIndex, TriadAnalysisModels.SceneObjectExtraction::new);
        List<TriadAnalysisModels.SceneEventExtraction> sceneEventExtractions = coalesce(extractedEventsBySceneIndex, TriadAnalysisModels.SceneEventExtraction::new);
        List<TriadAnalysisModels.SceneRelationClaimExtraction> sceneRelationClaimExtractions = coalesce(extractedRelationClaimsBySceneIndex, TriadAnalysisModels.SceneRelationClaimExtraction::new);

        return new TriadAnalysisModels.SceneRelationshipOutcome(
                analyses,
                sceneExtractions,
                sceneCollectiveExtractions,
                sceneObjectExtractions,
                sceneLocationExtractions,
                sceneEventExtractions,
                sceneRelationClaimExtractions
        );
    }

    private TriadStructuredResult analyzeTriadWithSemanticRetry(UUID jobId,
                                                               String systemPrompt,
                                                               Map<String, Object> userVariables,
                                                               TriadBuilderService.SceneTriad triad,
                                                               Map<String, Object> statusProps) {
        TriadAnalysisException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_SEMANTIC_TRIAD_ATTEMPTS; attempt++) {
            try {
                TriadStructuredResult parsed = llmClient.detectSceneAnalysisTriad(
                        jobId,
                        systemPrompt,
                        userVariables,
                        TriadStructuredResult.class
                );
                return validateAndNormalizeTriadResult(parsed, triad, statusProps);
            } catch (TriadAnalysisException e) {
                if (!isRetryableTriadAnalysisFailure(e) || attempt == MAX_SEMANTIC_TRIAD_ATTEMPTS) {
                    throw e;
                }
                lastFailure = e;
                LOG.warn("[TRIAD_ANALYSIS] Retrying semantic validation: jobId={}, triadIndex={}, attempt={}/{}, failureCode={}, message={}",
                        jobId,
                        statusProps.get("triadIndex"),
                        attempt + 1,
                        MAX_SEMANTIC_TRIAD_ATTEMPTS,
                        e.failure() != null ? e.failure().code() : null,
                        e.getMessage());
            }
        }

        throw lastFailure != null ? lastFailure : triadFailure(
                "TRIAD_RESPONSE_MISSING",
                "Triad analysis returned no structured result",
                triad,
                statusProps,
                null
        );
    }

    private boolean isRetryableTriadAnalysisFailure(TriadAnalysisException exception) {
        return exception.failure() != null
                && RETRYABLE_TRIAD_ANALYSIS_FAILURE_CODES.contains(exception.failure().code());
    }

    private List<TriadAnalysisModels.IndividualExtraction> normalizeIndividuals(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().individuals() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().individuals().stream()
                .filter(individual -> individual != null)
                .map(individual -> new TriadAnalysisModels.IndividualExtraction(
                        normalizeAliases(individual.aliases()),
                        normalizeText(individual.physicalProperties()),
                        normalizeText(individual.age()),
                        normalizeText(individual.activity())
                ))
                .toList();
    }

    private List<TriadAnalysisModels.LocationExtraction> normalizeLocations(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().locations() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().locations().stream()
                .filter(location -> location != null)
                .map(location -> new TriadAnalysisModels.LocationExtraction(
                        normalizeText(location.primaryName()),
                        normalizeAliases(location.aliases()),
                        normalizeText(location.kind()),
                        normalizeText(location.region()),
                        normalizeText(location.description())
                ))
                .toList();
    }

    private List<TriadAnalysisModels.ObjectExtraction> normalizeObjects(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().objects() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().objects().stream()
                .filter(object -> object != null)
                .map(object -> new TriadAnalysisModels.ObjectExtraction(
                        normalizeAliases(object.aliases()),
                        normalizeText(object.type()),
                        normalizeText(object.material()),
                        normalizeText(object.purpose()),
                        normalizeText(object.description())
                ))
                .filter(object -> object.type() != null || !object.aliases().isEmpty())
                .toList();
    }

    private List<TriadAnalysisModels.CollectiveExtraction> normalizeCollectives(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().collectives() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().collectives().stream()
                .filter(collective -> collective != null)
                .map(collective -> new TriadAnalysisModels.CollectiveExtraction(
                        normalizeAliases(collective.aliases()),
                        normalizeText(collective.collectiveType()),
                        normalizeText(collective.certainty()),
                        normalizeText(collective.evidence())
                ))
                .filter(collective -> !collective.aliases().isEmpty())
                .toList();
    }

    private List<TriadAnalysisModels.EventExtraction> normalizeEvents(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().events() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().events().stream()
                .filter(event -> event != null)
                .map(event -> new TriadAnalysisModels.EventExtraction(
                        normalizeText(event.name()),
                        normalizeText(event.eventType()),
                        normalizeText(event.description()),
                        normalizeEventTemporalType(event.temporalType()),
                        normalizeText(event.certainty()),
                        normalizeText(event.evidence())
                ))
                .filter(event -> event.name() != null)
                .toList();
    }

    private String normalizeEventTemporalType(String temporalType) {
        String normalized = normalizeText(temporalType);
        return normalized == null ? null : normalizeTemporalType(normalized);
    }

    private List<TriadAnalysisModels.RelationClaimExtraction> normalizeRelationClaims(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().relations() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().relations().stream()
                .filter(claim -> claim != null)
                .map(claim -> {
                    String[] subjectParts = parseEntityRef(claim.subject());
                    String[] objectParts = parseEntityRef(claim.object());

                    String normalizedRelationName = normalizeText(claim.relationName());
                    if (normalizedRelationName != null) {
                        normalizedRelationName = normalizedRelationName.replaceAll("\\s+", " ");
                    }

                    String definitionKey = generateDefinitionKey(normalizedRelationName);

                    String normalizedDescription = truncate(normalizeText(claim.relationDescription()), 1000);
                    String normalizedEvidence = truncate(normalizeText(claim.evidence()), 500);

                    return new TriadAnalysisModels.RelationClaimExtraction(
                            definitionKey,
                            subjectParts[0],
                            subjectParts[1],
                            normalizedRelationName,
                            normalizedDescription,
                            objectParts[0],
                            objectParts[1],
                            normalizeCertainty(claim.certainty()),
                            normalizedEvidence
                    );
                })
                .toList();
    }

    private static final Set<String> VALID_ENTITY_KINDS = Set.of(
            "Individual", "Collective", "Object", "Location", "Concept", "Event"
    );

    private String[] parseEntityRef(String entityRef) {
        String normalized = normalizeText(entityRef);
        if (normalized == null) {
            return new String[]{null, null};
        }
        // Try "Kind: Name" format first (prompt-specified)
        int colonSpaceIdx = normalized.indexOf(": ");
        if (colonSpaceIdx > 0 && colonSpaceIdx < normalized.length() - 2) {
            String kind = normalized.substring(0, colonSpaceIdx).trim();
            String name = normalized.substring(colonSpaceIdx + 2).trim();
            return new String[]{validateKind(kind), name};
        }
        // Fallback: try "Kind:Name" (no space after colon) — LLM deviation
        int colonIdx = normalized.indexOf(':');
        if (colonIdx > 0 && colonIdx < normalized.length() - 1) {
            String kind = normalized.substring(0, colonIdx).trim();
            String name = normalized.substring(colonIdx + 1).trim();
            if (!name.isEmpty()) {
                return new String[]{validateKind(kind), name};
            }
        }
        LOG.debug("[RELATION_CLAIM] Entity ref missing kind separator: entityRef={}", normalized);
        return new String[]{null, normalized};
    }

    private String validateKind(String kind) {
        if (kind == null || !VALID_ENTITY_KINDS.contains(kind)) {
            LOG.warn("[RELATION_CLAIM] Unknown entity kind '{}': falling back to null", kind);
            return null;
        }
        return kind;
    }

    private String generateDefinitionKey(String relationName) {
        if (relationName == null) {
            return null;
        }
        String id = relationName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")  // strip non-alpha chars first, preserve spaces
                .trim()
                .replaceAll("\\s+", "_");           // then normalize whitespace to underscores
        if (id.isEmpty()) {
            LOG.debug("[RELATION_CLAIM] Definition key empty after normalization for relationName='{}', using 'unparseable'", relationName);
            return "R:unparseable";
        }
        return "R:" + id;
    }

    private String normalizeCertainty(String certainty) {
        String normalized = normalizeText(certainty);
        if (normalized == null) {
            return "WeaklyImplied";
        }
        String lower = normalized.toLowerCase();
        if (lower.contains("explicit")) {
            return "Explicit";
        }
        if (lower.contains("strongly") && lower.contains("impl")) {
            return "StronglyImplied";
        }
        if (lower.contains("weakly") || lower.equals("implied")) {
            return "WeaklyImplied";
        }
        if (lower.contains("impl")) {
            return "WeaklyImplied";
        }
        return "WeaklyImplied";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        // Code-point-aware truncation to avoid splitting surrogate pairs
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxLength) {
            return value;
        }
        int offset = value.offsetByCodePoints(0, maxLength);
        return value.substring(0, offset) + "…";
    }

    private List<String> normalizeAliases(List<String> aliases) {
        if (aliases == null) {
            return List.of();
        }
        return aliases.stream()
                .map(this::normalizeText)
                .filter(alias -> alias != null)
                .toList();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TriadStructuredResult validateAndNormalizeTriadResult(TriadStructuredResult parsed,
                                                                  TriadBuilderService.SceneTriad triad,
                                                                  Map<String, Object> statusProps) {
        if (parsed == null) {
            throw triadFailure("TRIAD_RESPONSE_MISSING", "Triad analysis returned no structured result", triad, statusProps, null);
        }

        TriadRelation previousToCurrent = null;
        TriadRelation currentToNext = null;

        if (triad.previous() != null) {
            previousToCurrent = validateAndNormalizeRelation("previousToCurrent", parsed.previousToCurrent(), triad, statusProps);
        }
        if (triad.next() != null) {
            currentToNext = validateAndNormalizeRelation("currentToNext", parsed.currentToNext(), triad, statusProps);
        }

        return new TriadStructuredResult(
                parsed.timelineMarker(),
                previousToCurrent,
                currentToNext,
                parsed.currentSceneEntities()
        );
    }

    private TriadRelation validateAndNormalizeRelation(String relationName,
                                                       TriadRelation relation,
                                                       TriadBuilderService.SceneTriad triad,
                                                       Map<String, Object> statusProps) {
        if (relation == null) {
            throw triadFailure("TRIAD_RELATION_MISSING",
                    "Triad analysis omitted required relation '" + relationName + "'",
                    triad,
                    statusProps,
                    relationName);
        }

        if (isBlank(relation.temporalType())) {
            throw triadFailure("TRIAD_RELATION_TYPE_MISSING",
                    "Triad analysis returned relation without temporalType",
                    triad,
                    statusProps,
                    relationName);
        }

        if (isBlank(relation.certainty())) {
            throw triadFailure("TRIAD_RELATION_CERTAINTY_MISSING",
                    "Triad analysis returned relation without certainty",
                    triad,
                    statusProps,
                    relationName);
        }

        String normalizedTemporalType = normalizeTemporalType(relation.temporalType());
        if (!ALLOWED_TRIAD_RELATIONS.contains(normalizedTemporalType)) {
            throw triadFailure("TRIAD_RELATION_TYPE_INVALID",
                    "Triad analysis returned unsupported temporalType '" + relation.temporalType() + "'",
                    triad,
                    withInvalidTemporalType(statusProps, relation.temporalType(), normalizedTemporalType),
                    relationName);
        }

        return new TriadRelation(
                normalizedTemporalType,
                relation.certainty().trim(),
                normalizeText(relation.evidence())
        );
    }

    private Map<String, Object> withInvalidTemporalType(Map<String, Object> statusProps,
                                                        String rawTemporalType,
                                                        String normalizedTemporalType) {
        Map<String, Object> failureProps = new LinkedHashMap<>(statusProps);
        failureProps.put("rawTemporalType", rawTemporalType);
        failureProps.put("normalizedTemporalType", normalizedTemporalType);
        failureProps.put("allowedTemporalTypes", String.join(", ", new LinkedHashSet<>(ALLOWED_TRIAD_RELATIONS)));
        return failureProps;
    }

    private String normalizeTemporalType(String temporalType) {
        String trimmed = temporalType.trim();
        String base = trimmed.toLowerCase().replace("r:temporal.", "");
        return switch (base) {
            case "before", "meets" -> "R:temporal.before";
            case "after", "met_by" -> "R:temporal.after";
            case "overlaps" -> "R:temporal.overlaps";
            case "contains" -> "R:temporal.contains";
            case "during" -> "R:temporal.during";
            case "equals" -> "R:temporal.overlaps";
            default -> trimmed;
        };
    }

    private String invertPrevToCurr(String prevToCurr) {
        if (prevToCurr == null) {
            return null;
        }
        String base = prevToCurr.trim().toLowerCase().replace("r:temporal.", "");
        return switch (base) {
            case "before", "meets" -> "R:temporal.after";
            case "after", "met_by" -> "R:temporal.before";
            case "overlaps" -> "R:temporal.overlapped_by";
            case "contains" -> "R:temporal.during";
            case "during" -> "R:temporal.contains";
            default -> null;
        };
    }

    private TriadAnalysisException triadFailure(String code,
                                                String message,
                                                TriadBuilderService.SceneTriad triad,
                                                Map<String, Object> statusProps,
                                                String relationName) {
        IngestionFailure.Builder builder = IngestionFailure.builder(code, message)
                .exceptionType(TriadAnalysisException.class.getSimpleName())
                .stage(IngestionStatus.SCENE_TRIAD_ANALYSIS.name())
                .detail("relation", relationName)
                .detail("triadIndex", statusProps.get("triadIndex"))
                .detail("previousSceneId", triad.previous() != null ? triad.previous().getEventId() : null)
                .detail("currentSceneId", triad.current() != null ? triad.current().getEventId() : null)
                .detail("nextSceneId", triad.next() != null ? triad.next().getEventId() : null);

        return new TriadAnalysisException(builder.build());
    }

    private static <T> void mergeIfNotEmpty(Map<Integer, List<T>> bucket, int sceneIndex, List<T> items) {
        if (!items.isEmpty()) {
            bucket.computeIfAbsent(sceneIndex, k -> new ArrayList<>()).addAll(items);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E, T> List<T> coalesce(
            Map<Integer, List<E>> bucket,
            java.util.function.BiFunction<Integer, List<E>, T> constructor) {
        return bucket.entrySet().stream()
                .map(e -> constructor.apply(e.getKey(), List.copyOf(e.getValue())))
                .sorted((a, b) -> Integer.compare(
                        ((TriadAnalysisModels.SceneExtraction) a).sceneIndex(),
                        ((TriadAnalysisModels.SceneExtraction) b).sceneIndex()))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> buildUserVars(Chapter chapter, TriadBuilderService.SceneTriad triad) {
        Map<String, Object> v = new HashMap<>();
        v.put("prev_context_summary", textOrEmpty(readContextSummary(triad.previous())));
        v.put("prev_time_indicators", ""); // placeholder until segmentation data is threaded
        v.put("prev_break_reason", "");
        v.put("prev_text", extractSceneText(chapter, triad.previous()));

        v.put("curr_context_summary", textOrEmpty(readContextSummary(triad.current())));
        v.put("curr_time_indicators", "");
        v.put("curr_break_reason", "");
        v.put("curr_text", extractSceneText(chapter, triad.current()));

        v.put("next_context_summary", textOrEmpty(readContextSummary(triad.next())));
        v.put("next_time_indicators", "");
        v.put("next_break_reason", "");
        v.put("next_text", extractSceneText(chapter, triad.next()));
        return v;
    }

    private String extractSceneText(Chapter chapter, Scene s) {
        String chapterText = readChapterRawText(chapter);
        if (chapterText == null || s == null) return "";
        try {
            int start = s.getStartOffset().intValue();
            int end = s.getEndOffset().intValue();
            if (start < 0 || end > chapterText.length() || start >= end) return "";
            return chapterText.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    private String readContextSummary(Scene scene) {
        if (scene == null) {
            return "";
        }
        String summary = scene.getContextSummary();
        return summary == null ? "" : summary;
    }

    private String readChapterRawText(Chapter chapter) {
        if (chapter == null) {
            return null;
        }
        return chapter.getRawText();
    }

    private String textOrEmpty(String v) {
        return v == null ? "" : v;
    }
}
