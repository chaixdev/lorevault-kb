package com.lorevault.api.content.timeline.application;

import com.lorevault.api.content.timeline.infrastructure.TemporalEdgeWriteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SceneTemporalRelationshipPersistenceService {

    private final TemporalEdgeWriteRepository temporalEdgeWriteRepository;

    public SceneTemporalRelationshipPersistenceService(TemporalEdgeWriteRepository temporalEdgeWriteRepository) {
        this.temporalEdgeWriteRepository = temporalEdgeWriteRepository;
    }

    @Transactional
    public void applyTemporalRelationships(List<TemporalEdgeWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        for (var request : requests) {
            upsertWithAmbiguityHandling(request);
        }
    }

    private void upsertWithAmbiguityHandling(TemporalEdgeWriteRequest request) {
        TemporalEdgeProvenance provenance = request.provenance();
        String normalizedIncomingRawType = normalizeTemporalType(request.temporalType());
        String existingDirectType = temporalEdgeWriteRepository.findTemporalRelationBetween(
                request.fromSceneId(),
                request.toSceneId()
        );
        String normalizedExistingDirectType = normalizeTemporalType(existingDirectType);

        if (isDirectEnclosureContradiction(normalizedExistingDirectType, normalizedIncomingRawType)) {
            temporalEdgeWriteRepository.upsertAmbiguousRelation(
                    request.fromSceneId(),
                    request.toSceneId(),
                    "inferred",
                    buildAmbiguityPayload(
                            existingDirectType,
                            normalizedExistingDirectType,
                            request.temporalType(),
                            normalizedIncomingRawType,
                            request.certainty(),
                            request.evidence(),
                            request.timelineMarker(),
                            provenance
                    ),
                    request.evidence(),
                    asString(provenance != null ? provenance.jobId() : null),
                    asString(provenance != null ? provenance.chapterId() : null),
                    asString(provenance != null ? provenance.statusRecordId() : null),
                    asString(provenance != null ? provenance.llmCallRecordId() : null)
            );
            return;
        }

        CanonicalTemporalRelation incoming = normalizeToCanonical(
                request.fromSceneId(),
                request.toSceneId(),
                request.temporalType()
        );
        ExistingCanonicalEdge existing = findExistingCanonicalEdge(incoming.fromId(), incoming.toId());
        String reconciledType = reconcileTemporalType(existing.normalizedType(), incoming.type());
        if (existing.normalizedType() != null && !existing.normalizedType().isBlank() && reconciledType == null) {
            temporalEdgeWriteRepository.upsertAmbiguousRelation(
                    incoming.fromId(),
                    incoming.toId(),
                    "inferred",
                    buildAmbiguityPayload(
                            existing.originalType(),
                            existing.normalizedType(),
                            request.temporalType(),
                            incoming.type(),
                            request.certainty(),
                            request.evidence(),
                            request.timelineMarker(),
                            provenance
                    ),
                    request.evidence(),
                    asString(provenance != null ? provenance.jobId() : null),
                    asString(provenance != null ? provenance.chapterId() : null),
                    asString(provenance != null ? provenance.statusRecordId() : null),
                    asString(provenance != null ? provenance.llmCallRecordId() : null)
            );
            return;
        }

        String typeToPersist = reconciledType != null ? reconciledType : incoming.type();
        temporalEdgeWriteRepository.upsertTemporalEdge(
                incoming.fromId(),
                incoming.toId(),
                typeToPersist,
                request.certainty(),
                mapCertaintyToWeight(request.certainty()),
                "inferred",
                appendArtifactProvenance(request.evidence(), request.timelineMarker(), provenance),
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

    private String appendArtifactProvenance(String evidence,
                                            String timelineMarker,
                                            TemporalEdgeProvenance provenance) {
        StringBuilder sb = new StringBuilder();
        if (evidence != null && !evidence.isBlank()) {
            sb.append("evidence=").append(evidence);
        }
        if (timelineMarker != null && !timelineMarker.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append("timelineMarker=").append(timelineMarker);
        }
        UUID statusRecordId = provenance == null ? null : provenance.statusRecordId();
        if (statusRecordId != null) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append("statusRecordId=").append(statusRecordId);
        }
        UUID llmCallRecordId = provenance == null ? null : provenance.llmCallRecordId();
        if (llmCallRecordId != null) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
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
                                         TemporalEdgeProvenance provenance) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("existingType", existingType);
        payload.put("normalizedExistingType", normalizedExistingType);
        payload.put("incomingType", incomingType);
        payload.put("normalizedIncomingType", normalizedIncomingType);
        payload.put("incomingCertainty", incomingCertainty);
        payload.put("evidence", evidence);
        payload.put("timelineMarker", timelineMarker);
        payload.put("statusRecordId", provenance != null ? provenance.statusRecordId() : null);
        payload.put("llmCallRecordId", provenance != null ? provenance.llmCallRecordId() : null);
        return payload.toString();
    }

    private String asString(UUID value) {
        return value == null ? null : value.toString();
    }

    private Double mapCertaintyToWeight(String certainty) {
        if (certainty == null) {
            return 0.5d;
        }
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

    private record CanonicalTemporalRelation(UUID fromId, UUID toId, String type) {
    }

    private record ExistingCanonicalEdge(String originalType, String normalizedType, UUID fromId, UUID toId) {
    }
}
