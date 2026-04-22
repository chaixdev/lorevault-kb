package com.lorevault.api.ai.application;

import com.lorevault.api.ai.infrastructure.PromptRepository;
import com.lorevault.api.ai.infrastructure.SceneDetectionClient;
import com.lorevault.api.ai.domain.TriadAnalysisException;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.timeline.TriadRelationInverter;
import com.lorevault.api.ingestion.application.IngestionJobService;
import lombok.RequiredArgsConstructor;
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

/**
 * Orchestrates triad-based scene analysis end-to-end, fully in-memory.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TriadOrchestrationService {

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

    public record TriadEventExtraction(
            String name,
            String eventType,
            String temporalType,
            String certainty,
            String evidence
    ) {}

    public record TriadCurrentSceneEntities(
            List<TriadIndividualExtraction> individuals,
            List<TriadLocationExtraction> locations,
            List<TriadEventExtraction> events
    ) {
        public TriadCurrentSceneEntities(
                List<TriadIndividualExtraction> individuals,
                List<TriadLocationExtraction> locations
        ) {
            this(individuals, locations, List.of());
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

    public record TriadSceneIndividualExtraction(int sceneIndex, List<TriadIndividualExtraction> individuals) {}

    public record TriadSceneLocationExtraction(int sceneIndex, List<TriadLocationExtraction> locations) {}

    public record TriadSceneEventExtraction(int sceneIndex, List<TriadEventExtraction> events) {}

    public record TriadAnalysis(
            Integer previousSceneIndex,
            Integer currentSceneIndex,
            Integer nextSceneIndex,
            String timelineMarker,
            String prevToCurrType,
            String prevToCurrCertainty,
            String prevToCurrEvidence,
            String currToNextType,
            String currToNextCertainty,
            String currToNextEvidence,
            String currVsPrevInverted // useful for labeling
    ) {}

    public record TriadOutcome(
            List<TriadAnalysis> triadAnalyses,
            List<TriadSceneIndividualExtraction> sceneIndividualExtractions,
            List<TriadSceneLocationExtraction> sceneLocationExtractions,
            List<TriadSceneEventExtraction> sceneEventExtractions
    ) {
        public TriadOutcome(
                List<TriadAnalysis> triadAnalyses,
                List<TriadSceneIndividualExtraction> sceneIndividualExtractions,
                List<TriadSceneLocationExtraction> sceneLocationExtractions
        ) {
            this(triadAnalyses, sceneIndividualExtractions, sceneLocationExtractions, List.of());
        }
    }

    private final TriadBuilderService triadBuilder;
    private final SceneDetectionClient sceneDetectionClient;
    private final PromptRepository promptRepository;
    private final IngestionJobService ingestionJobService;

    /**
     * Analyze scene triads and return normalized results.
     */
    public TriadOutcome analyzeChapterTriadsWithIndividuals(UUID jobId, Chapter chapter) {
        List<TriadBuilderService.SceneTriad> triads = triadBuilder.buildTriadsForChapter(chapter);
        if (triads.isEmpty()) {
            return new TriadOutcome(List.of(), List.of(), List.of());
        }

        PromptTemplate systemTemplate = promptRepository.get("scene-analysis");
        String systemPrompt = systemTemplate.render(Map.of());

        List<TriadAnalysis> analyses = new ArrayList<>();
        Map<Integer, List<TriadIndividualExtraction>> extractedIndividualsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadLocationExtraction>> extractedLocationsBySceneIndex = new HashMap<>();
        Map<Integer, List<TriadEventExtraction>> extractedEventsBySceneIndex = new HashMap<>();

        int triadIndex = 0;
        for (TriadBuilderService.SceneTriad t : triads) {
            Map<String, Object> vars = buildUserVars(chapter, t);

            Map<String, Object> statusProps = new HashMap<>();
            statusProps.put("triadIndex", triadIndex++);
            statusProps.put("prevSceneIndex", t.previous() != null ? t.previous().getSceneIndex() : null);
            statusProps.put("currentSceneIndex", t.current().getSceneIndex());
            statusProps.put("nextSceneIndex", t.next() != null ? t.next().getSceneIndex() : null);

            log.debug("TriadOrchestrator: emitting status SCENE_TRIAD_ANALYSIS for triadIndex={} prevIdx={} currIdx={} nextIdx={}",
                    statusProps.get("triadIndex"), statusProps.get("prevSceneIndex"), statusProps.get("currentSceneIndex"), statusProps.get("nextSceneIndex"));
            ingestionJobService.updateJobStatus(
                    jobId,
                    IngestionStatus.SCENE_TRIAD_ANALYSIS,
                    "Triad analysis for scenes [prev, curr, next]",
                    statusProps
            );

            TriadStructuredResult parsed = sceneDetectionClient.detectSceneAnalysisTriad(
                    jobId,
                    systemPrompt,
                    vars,
                    TriadStructuredResult.class
            );
            TriadStructuredResult normalized = validateAndNormalizeTriadResult(parsed, t, statusProps);

            String inv = normalized.previousToCurrent() != null
                    ? TriadRelationInverter.invertPrevToCurr(normalized.previousToCurrent().temporalType())
                    : null;

            analyses.add(new TriadAnalysis(
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
                List<TriadIndividualExtraction> triadIndividuals = normalizeIndividuals(normalized);
                if (!triadIndividuals.isEmpty()) {
                    extractedIndividualsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadIndividuals);
                }

                List<TriadLocationExtraction> triadLocations = normalizeLocations(normalized);
                if (!triadLocations.isEmpty()) {
                    extractedLocationsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadLocations);
                }

                List<TriadEventExtraction> triadEvents = normalizeEvents(normalized);
                if (!triadEvents.isEmpty()) {
                    extractedEventsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadEvents);
                }
            }
        }

        List<TriadSceneIndividualExtraction> sceneExtractions = extractedIndividualsBySceneIndex.entrySet().stream()
                .map(e -> new TriadSceneIndividualExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadSceneIndividualExtraction::sceneIndex))
                .toList();

        List<TriadSceneLocationExtraction> sceneLocationExtractions = extractedLocationsBySceneIndex.entrySet().stream()
                .map(e -> new TriadSceneLocationExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadSceneLocationExtraction::sceneIndex))
                .toList();

        List<TriadSceneEventExtraction> sceneEventExtractions = extractedEventsBySceneIndex.entrySet().stream()
                .map(e -> new TriadSceneEventExtraction(e.getKey(), List.copyOf(e.getValue())))
                .sorted(java.util.Comparator.comparingInt(TriadSceneEventExtraction::sceneIndex))
                .toList();

        return new TriadOutcome(analyses, sceneExtractions, sceneLocationExtractions, sceneEventExtractions);
    }

    public List<TriadAnalysis> analyzeChapterTriads(UUID jobId, Chapter chapter) {
        return analyzeChapterTriadsWithIndividuals(jobId, chapter).triadAnalyses();
    }

    private List<TriadIndividualExtraction> normalizeIndividuals(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().individuals() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().individuals().stream()
                .filter(individual -> individual != null)
                .map(individual -> new TriadIndividualExtraction(
                        normalizeAliases(individual.aliases()),
                        normalizeText(individual.physicalProperties()),
                        normalizeText(individual.age()),
                        normalizeText(individual.activity())
                ))
                .toList();
    }

    private List<TriadLocationExtraction> normalizeLocations(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().locations() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().locations().stream()
                .filter(location -> location != null)
                .map(location -> new TriadLocationExtraction(
                        normalizeText(location.primaryName()),
                        normalizeAliases(location.aliases()),
                        normalizeText(location.kind()),
                        normalizeText(location.region()),
                        normalizeText(location.description())
                ))
                .toList();
    }

    private List<TriadEventExtraction> normalizeEvents(TriadStructuredResult parsed) {
        if (parsed == null || parsed.currentSceneEntities() == null || parsed.currentSceneEntities().events() == null) {
            return List.of();
        }
        return parsed.currentSceneEntities().events().stream()
                .filter(event -> event != null)
                .map(event -> new TriadEventExtraction(
                        normalizeText(event.name()),
                        normalizeText(event.eventType()),
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
