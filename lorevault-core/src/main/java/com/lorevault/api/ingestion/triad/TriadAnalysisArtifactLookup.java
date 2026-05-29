package com.lorevault.api.ingestion.triad;

import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;

import java.util.Optional;
import java.util.UUID;

public interface TriadAnalysisArtifactLookup {

    Optional<UUID> findLatestJobIdByChapterId(UUID chapterId);

    Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId);
}
