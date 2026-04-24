package com.lorevault.api.ingestion.domain;

import java.util.Optional;
import java.util.UUID;

public interface TriadAnalysisArtifactLookup {

    Optional<UUID> findLatestJobIdByChapterId(UUID chapterId);

    Optional<StatusRecord> findLatestTriadStatusByCurrentSceneId(UUID jobId, UUID currentSceneId);

    Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID statusRecordId);
}
