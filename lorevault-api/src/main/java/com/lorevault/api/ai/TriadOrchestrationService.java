package com.lorevault.api.ai;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.ingestion.IngestionFailure;
import com.lorevault.api.ingestion.IngestionStatus;
import com.lorevault.api.timeline.TriadRelationInverter;
import com.lorevault.api.ingestion.IngestionJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates triad-based scene analysis end-to-end, fully in-memory.
 */
@Service
public class TriadOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(TriadOrchestrationService.class);

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

    public record TriadCurrentSceneEntities(
            List<TriadIndividualExtraction> individuals,
            List<TriadLocationExtraction> locations
    ) {}

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

    public record TriadAnalysis(
            UUID previousSceneId,
            UUID currentSceneId,
            UUID nextSceneId,
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
            List<TriadSceneLocationExtraction> sceneLocationExtractions
    ) {}

    private final TriadBuilderService triadBuilder;
    private final SceneDetectionClient sceneDetectionClient;
    private final PromptRepository promptRepository;
    private final IngestionJobService ingestionJobService;

    public TriadOrchestrationService(TriadBuilderService triadBuilder,
                                     SceneDetectionClient sceneDetectionClient,
                                     PromptRepository promptRepository,
                                     IngestionJobService ingestionJobService) {
        this.triadBuilder = triadBuilder;
        this.sceneDetectionClient = sceneDetectionClient;
        this.promptRepository = promptRepository;
        this.ingestionJobService = ingestionJobService;
    }

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

        int triadIndex = 0;
        for (TriadBuilderService.SceneTriad t : triads) {
            Map<String, Object> vars = buildUserVars(chapter, t);

            Map<String, Object> statusProps = new HashMap<>();
            statusProps.put("triadIndex", triadIndex++);
            statusProps.put("prevSceneId", t.previous() != null ? t.previous().getEventId() : null);
            statusProps.put("currentSceneId", t.current().getEventId());
            statusProps.put("nextSceneId", t.next() != null ? t.next().getEventId() : null);

            log.debug("TriadOrchestrator: emitting status SCENE_TRIAD_ANALYSIS for triadIndex={} prev={} curr={} next={}",
                    statusProps.get("triadIndex"), statusProps.get("prevSceneId"), statusProps.get("currentSceneId"), statusProps.get("nextSceneId"));
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
            validateTriadResult(parsed, t, statusProps);

            String inv = parsed.previousToCurrent() != null
                    ? TriadRelationInverter.invertPrevToCurr(parsed.previousToCurrent().temporalType())
                    : null;

            analyses.add(new TriadAnalysis(
                    t.previous() != null ? t.previous().getEventId() : null,
                    t.current().getEventId(),
                    t.next() != null ? t.next().getEventId() : null,
                    parsed.timelineMarker(),
                    parsed.previousToCurrent() != null ? parsed.previousToCurrent().temporalType() : null,
                    parsed.previousToCurrent() != null ? parsed.previousToCurrent().certainty() : null,
                    parsed.previousToCurrent() != null ? parsed.previousToCurrent().evidence() : null,
                    parsed.currentToNext() != null ? parsed.currentToNext().temporalType() : null,
                    parsed.currentToNext() != null ? parsed.currentToNext().certainty() : null,
                    parsed.currentToNext() != null ? parsed.currentToNext().evidence() : null,
                    inv
            ));

            int sceneIndex = t.current().getSceneIndex() == null ? -1 : t.current().getSceneIndex();
            if (sceneIndex >= 0) {
                List<TriadIndividualExtraction> triadIndividuals = normalizeIndividuals(parsed);
                if (!triadIndividuals.isEmpty()) {
                    extractedIndividualsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadIndividuals);
                }

                List<TriadLocationExtraction> triadLocations = normalizeLocations(parsed);
                if (!triadLocations.isEmpty()) {
                    extractedLocationsBySceneIndex
                            .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
                            .addAll(triadLocations);
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

        return new TriadOutcome(analyses, sceneExtractions, sceneLocationExtractions);
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

    private void validateTriadResult(TriadStructuredResult parsed,
                                     TriadBuilderService.SceneTriad triad,
                                     Map<String, Object> statusProps) {
        if (parsed == null) {
            throw triadFailure("TRIAD_RESPONSE_MISSING", "Triad analysis returned no structured result", triad, statusProps, null);
        }

        if (triad.previous() != null) {
            validateRelation("previousToCurrent", parsed.previousToCurrent(), triad, statusProps);
        }
        if (triad.next() != null) {
            validateRelation("currentToNext", parsed.currentToNext(), triad, statusProps);
        }
    }

    private void validateRelation(String relationName,
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
        Object value = new BeanWrapperImpl(scene).getPropertyValue("contextSummary");
        String summary = value == null ? null : value.toString();
        return summary == null ? "" : summary;
    }

    private String readChapterRawText(Chapter chapter) {
        if (chapter == null) {
            return null;
        }
        Object value = new BeanWrapperImpl(chapter).getPropertyValue("rawText");
        return value == null ? null : value.toString();
    }

    private String textOrEmpty(String v) {
        return v == null ? "" : v;
    }
}
