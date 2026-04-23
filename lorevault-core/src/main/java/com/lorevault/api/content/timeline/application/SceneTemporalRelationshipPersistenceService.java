package com.lorevault.api.content.timeline.application;

import com.lorevault.api.ai.domain.TriadAnalysisException;
import com.lorevault.api.ai.application.SceneRelationshipAnalysisService;
import com.lorevault.api.content.timeline.infrastructure.TemporalEdgeWriteRepository;
import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.infrastructure.LlmCallRecordGraphRepository;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.infrastructure.StatusRecordGraphRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SceneTemporalRelationshipPersistenceService {

    private final TemporalEdgeWriteRepository temporalEdgeWriteRepository;
    private final IngestionJobGraphRepository ingestionJobGraphRepository;
    private final StatusRecordGraphRepository statusRecordGraphRepository;
    private final LlmCallRecordGraphRepository llmCallRecordGraphRepository;

    public SceneTemporalRelationshipPersistenceService(TemporalEdgeWriteRepository temporalEdgeWriteRepository,
                                                       IngestionJobGraphRepository ingestionJobGraphRepository,
                                                       StatusRecordGraphRepository statusRecordGraphRepository,
                                                       LlmCallRecordGraphRepository llmCallRecordGraphRepository) {
        this.temporalEdgeWriteRepository = temporalEdgeWriteRepository;
        this.ingestionJobGraphRepository = ingestionJobGraphRepository;
        this.statusRecordGraphRepository = statusRecordGraphRepository;
        this.llmCallRecordGraphRepository = llmCallRecordGraphRepository;
    }

    @Transactional
    public void applyTriadAnalysesPostPersistence(UUID chapterId,
                                                  List<SceneRelationshipAnalysisService.SceneRelationshipAnalysis> analyses,
                                                  Map<Integer, UUID> sceneIndexToPersistedId) {
        if (analyses == null || analyses.isEmpty()) {
            return;
        }

        UUID jobId = resolveLatestJobId(chapterId);

        for (var a : analyses) {
            UUID currentScenePersistedId = resolvePersistedSceneId(a.currentSceneId(), a.currentSceneIndex(), sceneIndexToPersistedId);

            if (a.previousSceneIndex() != null && a.currentSceneIndex() != null && a.prevToCurrType() != null) {
                UUID fromId = resolvePersistedSceneId(a.previousSceneId(), a.previousSceneIndex(), sceneIndexToPersistedId);
                UUID toId = resolvePersistedSceneId(a.currentSceneId(), a.currentSceneIndex(), sceneIndexToPersistedId);
                if (fromId != null && toId != null) {
                    upsertWithAmbiguityHandling(jobId, chapterId, currentScenePersistedId, a.currentSceneIndex(), fromId, toId,
                            a.prevToCurrType(), a.prevToCurrCertainty(), a.prevToCurrEvidence(), a.timelineMarker());
                }
            }

            if (a.currentSceneIndex() != null && a.nextSceneIndex() != null && a.currToNextType() != null) {
                UUID fromId = resolvePersistedSceneId(a.currentSceneId(), a.currentSceneIndex(), sceneIndexToPersistedId);
                UUID toId = resolvePersistedSceneId(a.nextSceneId(), a.nextSceneIndex(), sceneIndexToPersistedId);
                if (fromId != null && toId != null) {
                    upsertWithAmbiguityHandling(jobId, chapterId, currentScenePersistedId, a.currentSceneIndex(), fromId, toId,
                            a.currToNextType(), a.currToNextCertainty(), a.currToNextEvidence(), a.timelineMarker());
                }
            }
        }
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

    private void upsertWithAmbiguityHandling(UUID jobId,
                                             UUID chapterId,
                                             UUID currentSceneId,
                                             Integer currentSceneIndex,
                                             UUID from,
                                             UUID to,
                                             String type,
                                             String certainty,
                                             String evidence,
                                             String timelineMarker) {
        StatusRecord statusRecord = findRequiredTriadStatus(jobId, currentSceneId, currentSceneIndex);
        UUID statusRecordId = statusRecord.getId();
        LlmCallRecord callRecord = findRequiredTriadCall(jobId, statusRecordId);

        if (callRecord.getResponseBody() == null || Boolean.TRUE.equals(callRecord.getTruncated())) {
            throw triadArtifactFailure(
                    "TRIAD_ARTIFACT_UNRECOVERABLE",
                    "Triad structured output is missing or truncated for scene index " + currentSceneIndex,
                    currentSceneIndex,
                    statusRecord,
                    callRecord
            );
        }

        String normalizedIncomingRawType = normalizeTemporalType(type);
        String existingDirectType = temporalEdgeWriteRepository.findTemporalRelationBetween(from, to);
        String normalizedExistingDirectType = normalizeTemporalType(existingDirectType);

        if (isDirectEnclosureContradiction(normalizedExistingDirectType, normalizedIncomingRawType)) {
            temporalEdgeWriteRepository.upsertAmbiguousRelation(
                    from,
                    to,
                    "inferred",
                    buildAmbiguityPayload(existingDirectType, normalizedExistingDirectType, type, normalizedIncomingRawType,
                            certainty, evidence, timelineMarker, statusRecord, callRecord),
                    evidence,
                    asString(jobId),
                    asString(chapterId),
                    asString(statusRecordId),
                    asString(callRecord.getId())
            );
            return;
        }

        CanonicalTemporalRelation incoming = normalizeToCanonical(from, to, type);
        ExistingCanonicalEdge existing = findExistingCanonicalEdge(incoming.fromId(), incoming.toId());
        String reconciledType = reconcileTemporalType(existing.normalizedType(), incoming.type());
        if (existing.normalizedType() != null && !existing.normalizedType().isBlank() && reconciledType == null) {
            temporalEdgeWriteRepository.upsertAmbiguousRelation(
                    incoming.fromId(),
                    incoming.toId(),
                    "inferred",
                    buildAmbiguityPayload(existing.originalType(), existing.normalizedType(), type, incoming.type(),
                            certainty, evidence, timelineMarker, statusRecord, callRecord),
                    evidence,
                    asString(jobId),
                    asString(chapterId),
                    asString(statusRecordId),
                    asString(callRecord.getId())
            );
            return;
        }

        String typeToPersist = reconciledType != null ? reconciledType : incoming.type();

        temporalEdgeWriteRepository.upsertTemporalEdge(
                incoming.fromId(),
                incoming.toId(),
                typeToPersist,
                certainty,
                mapCertaintyToWeight(certainty),
                "inferred",
                appendArtifactProvenance(evidence, timelineMarker, statusRecord, callRecord),
                null,
                null,
                null
        );
    }

    private ExistingCanonicalEdge findExistingCanonicalEdge(UUID fromId, UUID toId) {
        String directType = temporalEdgeWriteRepository.findTemporalRelationBetween(fromId, toId);
        if (directType != null && !directType.isBlank()) {
            CanonicalTemporalRelation normalizedDirect = normalizeToCanonical(fromId, toId, directType);
            return new ExistingCanonicalEdge(directType, normalizedDirect.type(), normalizedDirect.fromId(), normalizedDirect.toId());
        }

        String reverseType = temporalEdgeWriteRepository.findTemporalRelationBetween(toId, fromId);
        if (reverseType != null && !reverseType.isBlank()) {
            CanonicalTemporalRelation normalizedReverse = normalizeToCanonical(toId, fromId, reverseType);
            return new ExistingCanonicalEdge(reverseType, normalizedReverse.type(), normalizedReverse.fromId(), normalizedReverse.toId());
        }

        return new ExistingCanonicalEdge(null, null, fromId, toId);
    }

    private UUID resolveLatestJobId(UUID chapterId) {
        if (chapterId == null) {
            return null;
        }
        return ingestionJobGraphRepository.findFirstByChapterIdOrderByCreatedAtDesc(chapterId)
                .map(IngestionJob::getId)
                .orElse(null);
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

        return statusRecordGraphRepository.findLatestTriadStatusByCurrentSceneId(jobId, currentSceneId.toString())
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
        UUID statusRecordId = statusRecord == null ? null : statusRecord.getId();
        if (statusRecordId != null) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("statusRecordId=").append(statusRecordId);
        }
        UUID llmCallRecordId = callRecord == null ? null : callRecord.getId();
        if (llmCallRecordId != null) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("llmCallRecordId=").append(llmCallRecordId);
        }
        return sb.toString();
    }

    private String buildAmbiguityPayload(String existingType,
                                         String normalizedExistingType,
                                         String incomingType,
                                         String normalizedIncomingType,
                                         String incomingCertainty,
                                         String evidence,
                                         String timelineMarker,
                                         StatusRecord statusRecord,
                                         LlmCallRecord callRecord) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("existingType", existingType);
        payload.put("normalizedExistingType", normalizedExistingType);
        payload.put("incomingType", incomingType);
        payload.put("normalizedIncomingType", normalizedIncomingType);
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

    private CanonicalTemporalRelation normalizeToCanonical(UUID fromId, UUID toId, String type) {
        if (type == null) {
            return new CanonicalTemporalRelation(fromId, toId, null);
        }

        String normalized = normalizeTemporalType(type);
        if (normalized == null || normalized.isBlank()) {
            return new CanonicalTemporalRelation(fromId, toId, normalized);
        }

        return switch (normalized) {
            case "R:temporal.after" -> new CanonicalTemporalRelation(toId, fromId, "R:temporal.before");
            case "R:temporal.overlapped_by" -> new CanonicalTemporalRelation(toId, fromId, "R:temporal.overlaps");
            case "R:temporal.contains" -> new CanonicalTemporalRelation(toId, fromId, "R:temporal.during");
            case "R:temporal.started_by" -> new CanonicalTemporalRelation(toId, fromId, "R:temporal.starts");
            case "R:temporal.finished_by" -> new CanonicalTemporalRelation(toId, fromId, "R:temporal.finishes");
            default -> new CanonicalTemporalRelation(fromId, toId, normalized);
        };
    }

    private String normalizeTemporalType(String type) {
        if (type == null) {
            return null;
        }

        String trimmed = type.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String base = trimmed.toLowerCase().replace("r:temporal.", "");
        return switch (base) {
            case "meets" -> "R:temporal.before";
            case "met_by" -> "R:temporal.after";
            case "equals" -> "R:temporal.overlaps";
            case "before", "after", "overlaps", "overlapped_by", "during", "contains",
                    "starts", "started_by", "finishes", "finished_by" -> "R:temporal." + base;
            default -> trimmed;
        };
    }

    private String reconcileTemporalType(String existingType, String incomingType) {
        if (incomingType == null || incomingType.isBlank()) {
            return incomingType;
        }
        if (existingType == null || existingType.isBlank()) {
            return incomingType;
        }
        if (existingType.equals(incomingType)) {
            return incomingType;
        }

        if (isOverlap(existingType) && isEnclosure(incomingType)) {
            return incomingType;
        }
        if (isEnclosure(existingType) && isOverlap(incomingType)) {
            return existingType;
        }

        return null;
    }

    private boolean isOverlap(String type) {
        return "R:temporal.overlaps".equals(type) || "R:temporal.overlapped_by".equals(type);
    }

    private boolean isEnclosure(String type) {
        return "R:temporal.during".equals(type);
    }

    private boolean isDirectEnclosureContradiction(String existingType, String incomingType) {
        if (existingType == null || incomingType == null) {
            return false;
        }
        return ("R:temporal.during".equals(existingType) && "R:temporal.contains".equals(incomingType))
                || ("R:temporal.contains".equals(existingType) && "R:temporal.during".equals(incomingType));
    }

    private record CanonicalTemporalRelation(UUID fromId, UUID toId, String type) {}

    private record ExistingCanonicalEdge(String originalType, String normalizedType, UUID fromId, UUID toId) {}

}
