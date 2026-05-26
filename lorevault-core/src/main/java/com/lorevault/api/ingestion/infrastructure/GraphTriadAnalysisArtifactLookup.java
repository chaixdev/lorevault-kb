package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.ai.infrastructure.PromptName;
import com.lorevault.api.ingestion.triad.TriadAnalysisArtifactLookup;
import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;
import com.lorevault.api.ingestion.job.ChapterIngestionJobGraphRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GraphTriadAnalysisArtifactLookup implements TriadAnalysisArtifactLookup {

    private final ChapterIngestionJobGraphRepository jobRepo;
    private final LlmCallRecordGraphRepository llmCallRepo;

    public GraphTriadAnalysisArtifactLookup(
            ChapterIngestionJobGraphRepository jobRepo,
            LlmCallRecordGraphRepository llmCallRepo) {
        this.jobRepo = jobRepo;
        this.llmCallRepo = llmCallRepo;
    }

    @Override
    public Optional<UUID> findLatestJobIdByChapterId(UUID chapterId) {
        if (chapterId == null) {
            return Optional.empty();
        }
        return jobRepo.findLatestJobIdByChapterId(chapterId);
    }

    /**
     * Triad stage correlation now uses Stage nodes instead of StatusRecord.
     * Returns null for now — triad temporal edges will be rebuilt during
     * the full triad refactoring pass.
     */
    @Override
    public Optional<UUID> findLatestTriadStageIdByCurrentSceneId(UUID jobId, UUID currentSceneId) {
        // StatusRecord-based lookup removed with the new Stage model.
        // Triad analysis status correlation needs a dedicated refactoring pass.
        return Optional.empty();
    }

    @Override
    public Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId) {
        if (jobId == null || stageId == null) {
            return Optional.empty();
        }
        return llmCallRepo.findLatestByJobStepAndStage(jobId, PromptName.SCENE_ANALYSIS.promptKey(), stageId);
    }
}
