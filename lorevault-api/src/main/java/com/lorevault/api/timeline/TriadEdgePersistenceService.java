package com.lorevault.api.timeline;

import com.lorevault.api.ai.TriadAnalysisException;
import com.lorevault.api.ai.TriadOrchestrationService;
import com.lorevault.api.ingestion.IngestionFailure;
import com.lorevault.api.ingestion.IngestionJob;
import com.lorevault.api.ingestion.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.LlmCallRecord;
import com.lorevault.api.ingestion.LlmCallRecordGraphRepository;
import com.lorevault.api.ingestion.StatusRecord;
import com.lorevault.api.ingestion.StatusRecordGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class TriadEdgePersistenceService {

    private final TemporalEdgeWriteRepository temporalEdgeWriteRepository;
    private final IngestionJobGraphRepository ingestionJobGraphRepository;
    private final StatusRecordGraphRepository statusRecordGraphRepository;
    private final LlmCallRecordGraphRepository llmCallRecordGraphRepository;

    @Transactional
    public void applyTriadAnalysesPostPersistence(UUID chapterId,
                                                  List<TriadOrchestrationService.TriadAnalysis> analyses,
                                                  Map<Integer, UUID> sceneIndexToPersistedId) {
        if (analyses == null || analyses.isEmpty()) {
            return;
        }

        UUID jobId = resolveLatestJobId(chapterId);

        for (var a : analyses) {
            if (a.previousSceneIndex() != null && a.currentSceneIndex() != null && a.prevToCurrType() != null) {
                UUID fromId = sceneIndexToPersistedId.get(a.previousSceneIndex());
                UUID toId = sceneIndexToPersistedId.get(a.currentSceneIndex());
                if (fromId != null && toId != null) {
                    upsertWithAmbiguityHandling(jobId, chapterId, a.currentSceneIndex(), fromId, toId,
                            a.prevToCurrType(), a.prevToCurrCertainty(), a.prevToCurrEvidence(), a.timelineMarker());
                }
            }

            if (a.currentSceneIndex() != null && a.nextSceneIndex() != null && a.currToNextType() != null) {
                UUID fromId = sceneIndexToPersistedId.get(a.currentSceneIndex());
                UUID toId = sceneIndexToPersistedId.get(a.nextSceneIndex());
                if (fromId != null && toId != null) {
                    upsertWithAmbiguityHandling(jobId, chapterId, a.currentSceneIndex(), fromId, toId,
                            a.currToNextType(), a.currToNextCertainty(), a.currToNextEvidence(), a.timelineMarker());
                }
            }
        }
    }

    private void upsertWithAmbiguityHandling(UUID jobId,
                                             UUID chapterId,
                                             Integer currentSceneIndex,
                                             UUID from,
                                             UUID to,
                                             String type,
                                             String certainty,
                                             String evidence,
                                             String timelineMarker) {
        StatusRecord statusRecord = findRequiredTriadStatus(jobId, currentSceneIndex);
        LlmCallRecord callRecord = findRequiredTriadCall(jobId, statusRecord.getId());

        if (callRecord.getResponseBody() == null || Boolean.TRUE.equals(callRecord.getTruncated())) {
            throw triadArtifactFailure(
                    "TRIAD_ARTIFACT_UNRECOVERABLE",
                    "Triad structured output is missing or truncated for scene index " + currentSceneIndex,
                    currentSceneIndex,
                    statusRecord,
                    callRecord
            );
        }

        String existingType = temporalEdgeWriteRepository.findTemporalRelationBetween(from, to);
        if (existingType != null && !existingType.isBlank() && !existingType.equals(type)) {
            temporalEdgeWriteRepository.upsertAmbiguousRelation(
                    from,
                    to,
                    "inferred",
                    buildAmbiguityPayload(existingType, type, certainty, evidence, timelineMarker, statusRecord, callRecord),
                    evidence,
                    asString(jobId),
                    asString(chapterId),
                    asString(statusRecord.getId()),
                    asString(callRecord.getId())
            );
            return;
        }

        temporalEdgeWriteRepository.upsertTemporalEdge(
                from,
                to,
                type,
                certainty,
                mapCertaintyToWeight(certainty),
                "inferred",
                appendArtifactProvenance(evidence, timelineMarker, statusRecord, callRecord),
                null,
                null,
                null
        );
    }

    private UUID resolveLatestJobId(UUID chapterId) {
        if (chapterId == null) {
            return null;
        }
        return ingestionJobGraphRepository.findFirstByChapterIdOrderByCreatedAtDesc(chapterId)
                .map(IngestionJob::getId)
                .orElse(null);
    }

    private StatusRecord findRequiredTriadStatus(UUID jobId, Integer currentSceneIndex) {
        if (jobId == null || currentSceneIndex == null) {
            throw triadArtifactFailure(
                    "TRIAD_STATUS_MISSING",
                    "Unable to resolve triad status record due to missing job or scene index",
                    currentSceneIndex,
                    null,
                    null
            );
        }

        String sceneIndexKey = currentSceneIndex.toString();
        return statusRecordGraphRepository.findTriadStatusesForJob(jobId).stream()
                .filter(status -> status != null && status.getProperties() != null)
                .filter(status -> {
                    String idx = status.getProperties().get("currentSceneIndex");
                    return Objects.equals(idx, sceneIndexKey);
                })
                .findFirst()
                .orElseThrow(() -> triadArtifactFailure(
                        "TRIAD_STATUS_MISSING",
                        "Missing SCENE_TRIAD_ANALYSIS status for scene index " + currentSceneIndex,
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

        return llmCallRecordGraphRepository
                .findLatestByJobStepAndStatusRecord(jobId, "scene-analysis", statusRecordId)
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

    private String appendArtifactProvenance(String evidence,
                                            String timelineMarker,
                                            StatusRecord statusRecord,
                                            LlmCallRecord callRecord) {
        StringBuilder sb = new StringBuilder();
        if (evidence != null && !evidence.isBlank()) {
            sb.append("evidence=").append(evidence);
        }
        if (timelineMarker != null && !timelineMarker.isBlank()) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("timelineMarker=").append(timelineMarker);
        }
        if (statusRecord != null && statusRecord.getId() != null) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("statusRecordId=").append(statusRecord.getId());
        }
        if (callRecord != null && callRecord.getId() != null) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("llmCallRecordId=").append(callRecord.getId());
        }
        return sb.toString();
    }

    private String buildAmbiguityPayload(String existingType,
                                         String incomingType,
                                         String incomingCertainty,
                                         String evidence,
                                         String timelineMarker,
                                         StatusRecord statusRecord,
                                         LlmCallRecord callRecord) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("existingType", existingType);
        payload.put("incomingType", incomingType);
        payload.put("incomingCertainty", incomingCertainty);
        payload.put("evidence", evidence);
        payload.put("timelineMarker", timelineMarker);
        payload.put("statusRecordId", statusRecord != null ? statusRecord.getId() : null);
        payload.put("llmCallRecordId", callRecord != null ? callRecord.getId() : null);
        return payload.toString();
    }

    private String asString(UUID value) {
        return value == null ? null : value.toString();
    }

    private Double mapCertaintyToWeight(String certainty) {
        if (certainty == null) return 0.5d;
        return switch (certainty) {
            case "Explicit" -> 0.95d;
            case "StronglyImplied" -> 0.8d;
            case "WeaklyImplied" -> 0.6d;
            case "Heuristic" -> 0.5d;
            default -> 0.5d;
        };
    }
}
