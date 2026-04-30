package com.lorevault.api.ingestion.triad;

import com.lorevault.api.ingestion.resolution.event.TemporalEdgeProvenance;
import com.lorevault.api.ingestion.resolution.event.TemporalEdgeWriteRequest;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;
import com.lorevault.api.ingestion.job.StatusRecord;

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
                                                        Map<Integer, UUID> sceneIndexToPersistedId) {
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
            TemporalEdgeProvenance provenance = resolveRequiredProvenance(
                    jobId,
                    chapterId,
                    currentScenePersistedId,
                    analysis.currentSceneIndex()
            );

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

    private TemporalEdgeProvenance resolveRequiredProvenance(UUID jobId,
                                                             UUID chapterId,
                                                             UUID currentSceneId,
                                                             Integer currentSceneIndex) {
        StatusRecord statusRecord = findRequiredTriadStatus(jobId, currentSceneId, currentSceneIndex);
        UUID statusRecordId = statusRecord.getId();
        LlmCallRecord callRecord = findRequiredTriadCall(jobId, statusRecordId);

        if (callRecord.getResponse() == null || callRecord.getResponse().getBody() == null) {
            throw triadArtifactFailure(
                    "TRIAD_ARTIFACT_UNRECOVERABLE",
                    "Triad structured output is missing for scene index " + currentSceneIndex,
                    currentSceneIndex,
                    statusRecord,
                    callRecord
            );
        }

        return new TemporalEdgeProvenance(jobId, chapterId, statusRecordId, callRecord.getId());
    }

    private StatusRecord findRequiredTriadStatus(UUID jobId,
                                                 UUID currentSceneId,
                                                 Integer currentSceneIndex) {
        if (jobId == null || currentSceneId == null) {
            throw triadArtifactFailure(
                    "TRIAD_STATUS_MISSING",
                    "Unable to resolve triad status record due to missing job or current scene id",
                    currentSceneIndex,
                    null,
                    null
            );
        }

        return triadAnalysisArtifactLookup.findLatestTriadStatusByCurrentSceneId(jobId, currentSceneId)
                .orElseThrow(() -> triadArtifactFailure(
                        "TRIAD_STATUS_MISSING",
                        "Missing SCENE_TRIAD_ANALYSIS status for current scene id " + currentSceneId,
                        currentSceneIndex,
                        null,
                        null
                ));
    }

    private LlmCallRecord findRequiredTriadCall(UUID jobId, UUID statusRecordId) {
        if (jobId == null || statusRecordId == null) {
            throw triadArtifactFailure(
                    "TRIAD_ARTIFACT_MISSING",
                    "Missing triad call linkage metadata",
                    null,
                    null,
                    null
            );
        }

        return triadAnalysisArtifactLookup.findLatestTriadCallRecord(jobId, statusRecordId)
                .orElseThrow(() -> triadArtifactFailure(
                        "TRIAD_ARTIFACT_MISSING",
                        "Missing scene-analysis LlmCallRecord for status " + statusRecordId,
                        null,
                        null,
                        null
                ));
    }

    private TriadAnalysisException triadArtifactFailure(String code,
                                                        String message,
                                                        Integer currentSceneIndex,
                                                        StatusRecord statusRecord,
                                                        LlmCallRecord callRecord) {
        IngestionFailure.Builder builder = IngestionFailure.builder(code, message)
                .exceptionType(TriadAnalysisException.class.getSimpleName())
                .stage("SCENE_TRIAD_ANALYSIS")
                .detail("currentSceneIndex", currentSceneIndex)
                .detail("statusRecordId", statusRecord != null ? statusRecord.getId() : null)
                .detail("llmCallRecordId", callRecord != null ? callRecord.getId() : null);
        return new TriadAnalysisException(builder.build());
    }
}
