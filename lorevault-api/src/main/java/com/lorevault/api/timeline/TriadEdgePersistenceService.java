package com.lorevault.api.timeline;

import com.lorevault.api.timeline.TemporalEdgeWriteRepository;
import com.lorevault.api.ai.TriadOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TriadEdgePersistenceService {

    private final TemporalEdgeWriteRepository temporalEdgeWriteRepository;

    public void applyTriadAnalyses(List<TriadOrchestrationService.TriadAnalysis> analyses) {
        if (analyses == null || analyses.isEmpty()) return;
        for (var a : analyses) {
            // prev -> curr
            if (a.previousSceneId() != null && a.currentSceneId() != null && a.prevToCurrType() != null) {
                upsert(a.previousSceneId(), a.currentSceneId(), a.prevToCurrType(), a.prevToCurrCertainty(), a.prevToCurrEvidence());
            }
            // curr -> next
            if (a.currentSceneId() != null && a.nextSceneId() != null && a.currToNextType() != null) {
                upsert(a.currentSceneId(), a.nextSceneId(), a.currToNextType(), a.currToNextCertainty(), a.currToNextEvidence());
            }
        }
    }

    private void upsert(java.util.UUID from, java.util.UUID to, String type, String certainty, String evidence) {
        try {
        Double weight = mapCertaintyToWeight(certainty);
        temporalEdgeWriteRepository.upsertTemporalEdge(
            from, to, type, certainty, weight, "ai-pass2-triad", evidence, null, null, null
        );
        } catch (Exception e) {
            log.warn("Failed to upsert TEMPORAL edge {} -> {} type {}: {}", from, to, type, e.getMessage());
        }
    }

    private Double mapCertaintyToWeight(String certainty) {
        if (certainty == null) return 0.5d;
        return switch (certainty) {
            case "Explicit" -> 0.9d;
            case "StronglyImplied" -> 0.7d;
            case "WeaklyImplied" -> 0.5d;
            case "Heuristic" -> 0.3d;
            default -> 0.5d;
        };
    }
}
