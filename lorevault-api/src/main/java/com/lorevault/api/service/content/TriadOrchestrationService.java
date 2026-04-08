package com.lorevault.api.service.content;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionFailure;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.timeline.TriadRelationInverter;
import com.lorevault.api.infrastructure.prompt.PromptRepositoryAdapter;
import com.lorevault.api.service.ingestion.IngestionJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates Pass 2 triad-based analysis end-to-end, fully in-memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriadOrchestrationService {

    public record TriadRelation(String temporalType, String certainty, String evidence) {}

    public record TriadStructuredResult(String timelineMarker, TriadRelation previousToCurrent, TriadRelation currentToNext) {}

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

    private final TriadBuilderService triadBuilder;
    private final SceneDetectionClient sceneDetectionClient;
    private final PromptRepositoryAdapter promptRepository;
    private final IngestionJobService ingestionJobService;

    /**
     * Analyze scene triads and return normalized results.
     */
    public List<TriadAnalysis> analyzeChapterTriads(UUID jobId, Chapter chapter) {
        List<TriadBuilderService.SceneTriad> triads = triadBuilder.buildTriadsForChapter(chapter);
        if (triads.isEmpty()) return List.of();

        // System prompt is pass2 system prompt
        PromptTemplate systemTemplate = promptRepository.get("scene-detection-pass2");
        String systemPrompt = systemTemplate.render(Map.of());

        List<TriadAnalysis> out = new ArrayList<>();
        int triadIndex = 0;
        for (TriadBuilderService.SceneTriad t : triads) {
            Map<String, Object> vars = buildUserVars(chapter, t);

            // Create per-triad status record so LLM call links to it
            Map<String, Object> statusProps = new HashMap<>();
            statusProps.put("triadIndex", triadIndex++);
            statusProps.put("prevSceneId", t.previous() != null ? t.previous().getId() : null);
            statusProps.put("currentSceneId", t.current().getId());
            statusProps.put("nextSceneId", t.next() != null ? t.next().getId() : null);

            log.debug("TriadOrchestrator: emitting status SCENE_TRIAD_ANALYSIS for triadIndex={} prev={} curr={} next={}",
                    statusProps.get("triadIndex"), statusProps.get("prevSceneId"), statusProps.get("currentSceneId"), statusProps.get("nextSceneId"));
            ingestionJobService.updateJobStatus(
                jobId,
                IngestionStatus.SCENE_TRIAD_ANALYSIS,
                "Triad analysis for scenes [prev, curr, next]",
                statusProps
            );

            TriadStructuredResult parsed = sceneDetectionClient.detectScenesPass2Triad(
                    jobId,
                    systemPrompt,
                    vars,
                    TriadStructuredResult.class
            );
            validateTriadResult(parsed, t, statusProps);

            String inv = parsed.previousToCurrent() != null
                    ? TriadRelationInverter.invertPrevToCurr(parsed.previousToCurrent().temporalType())
                    : null;

        out.add(new TriadAnalysis(
            t.previous() != null ? t.previous().getId() : null,
            t.current().getId(),
            t.next() != null ? t.next().getId() : null,
                    parsed.timelineMarker(),
                    parsed.previousToCurrent() != null ? parsed.previousToCurrent().temporalType() : null,
                    parsed.previousToCurrent() != null ? parsed.previousToCurrent().certainty() : null,
                    parsed.previousToCurrent() != null ? parsed.previousToCurrent().evidence() : null,
                    parsed.currentToNext() != null ? parsed.currentToNext().temporalType() : null,
                    parsed.currentToNext() != null ? parsed.currentToNext().certainty() : null,
                    parsed.currentToNext() != null ? parsed.currentToNext().evidence() : null,
                    inv
            ));
        }
        return out;
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
                .detail("previousSceneId", triad.previous() != null ? triad.previous().getId() : null)
                .detail("currentSceneId", triad.current() != null ? triad.current().getId() : null)
                .detail("nextSceneId", triad.next() != null ? triad.next().getId() : null);

        return new TriadAnalysisException(builder.build());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> buildUserVars(Chapter chapter, TriadBuilderService.SceneTriad triad) {
        Map<String, Object> v = new HashMap<>();
        v.put("prev_context_summary", textOrEmpty(triad.previous(), Scene::getContextSummary));
        v.put("prev_time_indicators", ""); // placeholder until pass1 data is threaded
        v.put("prev_break_reason", "");
        v.put("prev_text", extractSceneText(chapter, triad.previous()));

        v.put("curr_context_summary", textOrEmpty(triad.current(), Scene::getContextSummary));
        v.put("curr_time_indicators", "");
        v.put("curr_break_reason", "");
        v.put("curr_text", extractSceneText(chapter, triad.current()));

        v.put("next_context_summary", textOrEmpty(triad.next(), Scene::getContextSummary));
        v.put("next_time_indicators", "");
        v.put("next_break_reason", "");
        v.put("next_text", extractSceneText(chapter, triad.next()));
        return v;
    }

    private String extractSceneText(Chapter chapter, Scene s) {
        if (chapter == null || chapter.getRawText() == null || s == null) return "";
        try {
            int start = s.getStartCharacterOffset().intValue();
            int end = s.getEndCharacterOffset().intValue();
            String text = chapter.getRawText();
            if (start < 0 || end > text.length() || start >= end) return "";
            return text.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    private String textOrEmpty(Scene s, java.util.function.Function<Scene, String> f) {
        if (s == null) return "";
        String v = f.apply(s);
        return v == null ? "" : v;
    }
}
