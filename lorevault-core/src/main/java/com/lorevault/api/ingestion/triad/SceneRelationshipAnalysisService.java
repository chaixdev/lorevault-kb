package com.lorevault.api.ingestion.triad;

import com.lorevault.api.ai.llm.LlmClient;
import com.lorevault.api.ai.infrastructure.PromptRepository;
import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.job.IngestionStatus;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SceneRelationshipAnalysisService {

    private static final Set<String> ALLOWED_TRIAD_RELATIONS = Set.of(
            "R:temporal.before",
            "R:temporal.after",
            "R:temporal.overlaps",
            "R:temporal.contains",
            "R:temporal.during"
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

    public record TriadCurrentSceneEntities(
            List<TriadIndividualExtraction> individuals,
            List<TriadCollectiveExtraction> collectives,
            List<TriadObjectExtraction> objects,
            List<TriadLocationExtraction> locations,
            List<TriadEventExtraction> events
    ) {
        public TriadCurrentSceneEntities(
                List<TriadIndividualExtraction> individuals,
                List<TriadLocationExtraction> locations
        ) {
            this(individuals, List.of(), List.of(), locations, List.of());
        }

        public TriadCurrentSceneEntities(
                List<TriadIndividualExtraction> individuals,
                List<TriadObjectExtraction> objects,
                List<TriadLocationExtraction> locations
        ) {
            this(individuals, List.of(), objects, locations, List.of());
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
    public TriadAnalysisModels.SceneRelationshipOutcome analyzeChapterTriadsWithIndividuals(UUID jobId, Chapter chapter) {
        return analyzeChapterTriadsWithIndividuals(jobId, chapter, ignored -> {
        });
    }

    public TriadAnalysisModels.SceneRelationshipOutcome analyzeChapterTriadsWithIndividuals(UUID jobId,
                                                                                             Chapter chapter,
                                                                                             Consumer<Map<String, Object>> onTriadStart) {
        List<TriadBuilderService.SceneTriad> triads = triadBuilder.buildTriadsForChapter(chapter);
        if (triads.isEmpty()) {
            return new TriadAnalysisModels.SceneRelationshipOutcome(List.of(), List.of(), List.of());
        }

        PromptTemplate systemTemplate = promptRepository.get("scene-analysis");
        String systemPrompt = systemTemplate.render(Map.of());

        List<TriadAnalysisModels.SceneRelationshipAnalysis> analyses = new ArrayList<>();
        Map<Integer, List<TriadAnalysisModels.IndividualExtraction>> extractedIndividualsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.CollectiveExtraction>> extractedCollectivesBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.ObjectExtraction>> extractedObjectsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.LocationExtraction>> extractedLocationsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadAnalysisModels.EventExtraction>> extractedEventsBySceneIndex = new HashMap<>();

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

            TriadStructuredResult parsed = llmClient.detectSceneAnalysisTriad(
                    jobId,
                    systemPrompt,
                    vars,
                    TriadStructuredResult.class
            );
            TriadStructuredResult normalized = validateAndNormalizeTriadResult(parsed, t, statusProps);

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
                List<TriadAnalysisModels.IndividualExtraction> triadIndividuals = normalizeIndividuals(normalized);
                if (!triadIndividuals.isEmpty()) {
                    extractedIndividualsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadIndividuals);
                }

                List<TriadAnalysisModels.LocationExtraction> triadLocations = normalizeLocations(normalized);
                if (!triadLocations.isEmpty()) {
                    extractedLocationsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadLocations);
                }

                List<TriadAnalysisModels.ObjectExtraction> triadObjects = normalizeObjects(normalized);
                if (!triadObjects.isEmpty()) {
                    extractedObjectsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadObjects);
                }

                List<TriadAnalysisModels.CollectiveExtraction> triadCollectives = normalizeCollectives(normalized);
                if (!triadCollectives.isEmpty()) {
                    extractedCollectivesBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadCollectives);
                }

                List<TriadAnalysisModels.EventExtraction> triadEvents = normalizeEvents(normalized);
                if (!triadEvents.isEmpty()) {
                    extractedEventsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadEvents);
                }
            }
        }

        List<TriadAnalysisModels.SceneIndividualExtraction> sceneExtractions = extractedIndividualsBySceneIndex.entrySet().stream()
                .map(e -> new TriadAnalysisModels.SceneIndividualExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadAnalysisModels.SceneIndividualExtraction::sceneIndex))
                .toList();

        List<TriadAnalysisModels.SceneLocationExtraction> sceneLocationExtractions = extractedLocationsBySceneIndex.entrySet().stream()
                .map(e -> new TriadAnalysisModels.SceneLocationExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadAnalysisModels.SceneLocationExtraction::sceneIndex))
                .toList();

        List<TriadAnalysisModels.SceneCollectiveExtraction> sceneCollectiveExtractions = extractedCollectivesBySceneIndex.entrySet().stream()
                .map(e -> new TriadAnalysisModels.SceneCollectiveExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadAnalysisModels.SceneCollectiveExtraction::sceneIndex))
                .toList();

        List<TriadAnalysisModels.SceneObjectExtraction> sceneObjectExtractions = extractedObjectsBySceneIndex.entrySet().stream()
                .map(e -> new TriadAnalysisModels.SceneObjectExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadAnalysisModels.SceneObjectExtraction::sceneIndex))
                .toList();

        List<TriadAnalysisModels.SceneEventExtraction> sceneEventExtractions = extractedEventsBySceneIndex.entrySet().stream()
                .map(e -> new TriadAnalysisModels.SceneEventExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadAnalysisModels.SceneEventExtraction::sceneIndex))
                .toList();

        return new TriadAnalysisModels.SceneRelationshipOutcome(
                analyses,
                sceneExtractions,
                sceneCollectiveExtractions,
                sceneObjectExtractions,
                sceneLocationExtractions,
                sceneEventExtractions
        );
    }

    public List<TriadAnalysisModels.SceneRelationshipAnalysis> analyzeChapterTriads(UUID jobId, Chapter chapter) {
        return analyzeChapterTriadsWithIndividuals(jobId, chapter).triadAnalyses();
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
