package com.lorevault.api.ingestion.triad;

import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;

import java.util.Optional;
import java.util.UUID;

public interface TriadAnalysisArtifactLookup {

    Optional<UUID> findLatestJobIdByChapterId(UUID chapterId);

    /**
     * Find the stage ID of the latest SCENE_TRIAD_ANALYSIS completion
     * for the given job and current scene.
     */
    Optional<UUID> findLatestTriadStageIdByCurrentSceneId(UUID jobId, UUID currentSceneId);

    Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId);
}
