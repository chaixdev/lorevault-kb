package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.ai.infrastructure.PromptName;
import com.lorevault.api.ingestion.triad.TriadAnalysisArtifactLookup;
import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;
import com.lorevault.api.ingestion.job.ChapterIngestionJobGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageKey;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GraphTriadAnalysisArtifactLookup implements TriadAnalysisArtifactLookup {

    private final ChapterIngestionJobGraphRepository jobRepo;
    private final LlmCallRecordGraphRepository llmCallRepo;
    private final StageGraphRepository stageRepo;

    public GraphTriadAnalysisArtifactLookup(
            ChapterIngestionJobGraphRepository jobRepo,
            LlmCallRecordGraphRepository llmCallRepo,
            StageGraphRepository stageRepo) {
        this.jobRepo = jobRepo;
        this.llmCallRepo = llmCallRepo;
        this.stageRepo = stageRepo;
    }

    @Override
    public Optional<UUID> findLatestJobIdByChapterId(UUID chapterId) {
        if (chapterId == null) {
            return Optional.empty();
        }
        return jobRepo.findLatestJobIdByChapterId(chapterId);
    }

    /**
     * Find the SCENE_SEGMENTATION stage for the given job.
     * Temporal edges produced by triad analysis are attributed to the
     * chapter's scene segmentation stage (per-chapter granularity).
     * Full per-scene provenance will be available after per-scene buildTriad is
     * adopted progressively in SceneDetectionHandler.
     */
    @Override
    public Optional<UUID> findLatestTriadStageIdByCurrentSceneId(UUID jobId, UUID currentSceneId) {
        if (jobId == null) {
            return Optional.empty();
        }
        return stageRepo.findByJobIdAndStep(jobId, StageKey.SCENE_SEGMENTATION)
                .map(stage -> stage.getId());
    }

    @Override
    public Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId) {
        if (jobId == null || stageId == null) {
            return Optional.empty();
        }
        return llmCallRepo.findLatestByJobStepAndStage(jobId, PromptName.SCENE_ANALYSIS.promptKey(), stageId);
    }
}
