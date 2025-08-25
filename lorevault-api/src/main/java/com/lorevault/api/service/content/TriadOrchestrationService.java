package com.lorevault.api.service.content;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.timeline.TriadRelationInverter;
import com.lorevault.api.service.shared.PromptLoaderService;
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
    private final TriadXmlParser triadXmlParser;
    private final PromptLoaderService promptLoaderService;

    /**
     * Analyze scene triads and return normalized results.
     */
    public List<TriadAnalysis> analyzeChapterTriads(UUID jobId, Chapter chapter) {
        List<TriadBuilderService.SceneTriad> triads = triadBuilder.buildTriadsForChapter(chapter);
        if (triads.isEmpty()) return List.of();

        // System prompt is pass2 system prompt
        PromptTemplate systemTemplate = promptLoaderService.getSceneDetectionPass2PromptTemplate();
        String systemPrompt = systemTemplate.render(Map.of());

        List<TriadAnalysis> out = new ArrayList<>();
        for (TriadBuilderService.SceneTriad t : triads) {
            Map<String, Object> vars = buildUserVars(chapter, t);
            String xml = sceneDetectionClient.detectScenesPass2Triad(jobId, systemPrompt, vars);
            TriadXmlParser.TriadResult parsed = triadXmlParser.parse(xml);
            String inv = parsed.prevToCurr() != null ?
                    TriadRelationInverter.invertPrevToCurr(parsed.prevToCurr().temporalType()) : null;

        out.add(new TriadAnalysis(
            t.previous() != null ? t.previous().getId() : null,
            t.current().getId(),
            t.next() != null ? t.next().getId() : null,
                    parsed.timelineMarker(),
                    parsed.prevToCurr() != null ? parsed.prevToCurr().temporalType() : null,
                    parsed.prevToCurr() != null ? parsed.prevToCurr().certainty() : null,
                    parsed.prevToCurr() != null ? parsed.prevToCurr().evidence() : null,
                    parsed.currToNext() != null ? parsed.currToNext().temporalType() : null,
                    parsed.currToNext() != null ? parsed.currToNext().certainty() : null,
                    parsed.currToNext() != null ? parsed.currToNext().evidence() : null,
                    inv
            ));
        }
        return out;
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
