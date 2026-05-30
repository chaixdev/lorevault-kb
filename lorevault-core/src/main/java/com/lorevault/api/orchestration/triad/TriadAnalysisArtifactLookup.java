package com.lorevault.api.orchestration.triad;

import com.lorevault.api.ai.telemetry.LlmCallRecord;

import java.util.Optional;
import java.util.UUID;

public interface TriadAnalysisArtifactLookup {

    Optional<UUID> findLatestJobIdByChapterId(UUID chapterId);

    Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId);
}
