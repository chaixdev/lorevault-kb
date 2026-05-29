package com.lorevault.api.ingestion.triad;

import com.lorevault.api.ingestion.resolution.event.TemporalEdgeProvenance;
import com.lorevault.api.ingestion.resolution.event.TemporalEdgeWriteRequest;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TriadTemporalEdgeRequestFactory {

    private final TriadAnalysisArtifactLookup triadAnalysisArtifactLookup;

    public TriadTemporalEdgeRequestFactory(TriadAnalysisArtifactLookup triadAnalysisArtifactLookup) {
        this.triadAnalysisArtifactLookup = triadAnalysisArtifactLookup;
    }

    public List<TemporalEdgeWriteRequest> buildRequests(UUID chapterId,
                                                        List<TriadAnalysisModels.SceneRelationshipAnalysis> analyses,
                                                        Map<Integer, UUID> sceneIndexToPersistedId,
                                                        UUID stageId) {
        if (analyses == null || analyses.isEmpty()) {
            return List.of();
        }

        UUID jobId = resolveLatestJobId(chapterId);
        List<TemporalEdgeWriteRequest> requests = new ArrayList<>();
        for (var analysis : analyses) {
            UUID currentScenePersistedId = resolvePersistedSceneId(
                    analysis.currentSceneId(),
                    analysis.currentSceneIndex(),
                    sceneIndexToPersistedId
            );
            TemporalEdgeProvenance provenance = new TemporalEdgeProvenance(
                    jobId, chapterId, stageId, null);

            if (analysis.previousSceneIndex() != null
                    && analysis.currentSceneIndex() != null
                    && analysis.prevToCurrType() != null) {
                UUID fromId = resolvePersistedSceneId(
                        analysis.previousSceneId(),
                        analysis.previousSceneIndex(),
                        sceneIndexToPersistedId
                );
                UUID toId = resolvePersistedSceneId(
                        analysis.currentSceneId(),
                        analysis.currentSceneIndex(),
                        sceneIndexToPersistedId
                );
                if (fromId != null && toId != null) {
                    requests.add(new TemporalEdgeWriteRequest(
                            fromId,
                            toId,
                            analysis.prevToCurrType(),
                            analysis.prevToCurrCertainty(),
                            analysis.prevToCurrEvidence(),
                            analysis.timelineMarker(),
                            provenance
                    ));
                }
            }

            if (analysis.currentSceneIndex() != null
                    && analysis.nextSceneIndex() != null
                    && analysis.currToNextType() != null) {
                UUID fromId = resolvePersistedSceneId(
                        analysis.currentSceneId(),
                        analysis.currentSceneIndex(),
                        sceneIndexToPersistedId
                );
                UUID toId = resolvePersistedSceneId(
                        analysis.nextSceneId(),
                        analysis.nextSceneIndex(),
                        sceneIndexToPersistedId
                );
                if (fromId != null && toId != null) {
                    requests.add(new TemporalEdgeWriteRequest(
                            fromId,
                            toId,
                            analysis.currToNextType(),
                            analysis.currToNextCertainty(),
                            analysis.currToNextEvidence(),
                            analysis.timelineMarker(),
                            provenance
                    ));
                }
            }
        }

        return List.copyOf(requests);
    }

    private UUID resolveLatestJobId(UUID chapterId) {
        if (chapterId == null) {
            return null;
        }
        return triadAnalysisArtifactLookup.findLatestJobIdByChapterId(chapterId).orElse(null);
    }

    private UUID resolvePersistedSceneId(UUID sceneId,
                                         Integer sceneIndex,
                                         Map<Integer, UUID> sceneIndexToPersistedId) {
        if (sceneId != null) {
            return sceneId;
        }
        if (sceneIndex == null || sceneIndexToPersistedId == null) {
            return null;
        }
        return sceneIndexToPersistedId.get(sceneIndex);
    }
}
