package com.lorevault.api.ingestion.triad;

import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;
import com.lorevault.api.ingestion.job.StatusRecord;

import java.util.Optional;
import java.util.UUID;

public interface TriadAnalysisArtifactLookup {

    Optional<UUID> findLatestJobIdByChapterId(UUID chapterId);

    Optional<StatusRecord> findLatestTriadStatusByCurrentSceneId(UUID jobId, UUID currentSceneId);

    Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID statusRecordId);
}
