package com.lorevault.api.orchestration.triad;

import com.lorevault.api.ai.infrastructure.LlmCallRecordGraphRepository;
import com.lorevault.api.ai.infrastructure.PromptName;
import com.lorevault.api.ai.telemetry.LlmCallRecord;
import com.lorevault.api.orchestration.job.ChapterIngestionJobGraphRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GraphTriadAnalysisArtifactLookup {

    private final ChapterIngestionJobGraphRepository jobRepo;
    private final LlmCallRecordGraphRepository llmCallRepo;

    public GraphTriadAnalysisArtifactLookup(
            ChapterIngestionJobGraphRepository jobRepo,
            LlmCallRecordGraphRepository llmCallRepo) {
        this.jobRepo = jobRepo;
        this.llmCallRepo = llmCallRepo;
    }

    public Optional<UUID> findLatestJobIdByChapterId(UUID chapterId) {
        if (chapterId == null) {
            return Optional.empty();
        }
        return jobRepo.findLatestJobIdByChapterId(chapterId);
    }

    public Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId) {
        if (jobId == null || stageId == null) {
            return Optional.empty();
        }
        return llmCallRepo.findLatestByJobStepAndStage(jobId, PromptName.SCENE_ANALYSIS.promptKey(), stageId);
    }
}
