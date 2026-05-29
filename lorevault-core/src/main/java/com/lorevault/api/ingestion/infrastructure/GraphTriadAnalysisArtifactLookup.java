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

    @Override
    public Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId) {
        if (jobId == null || stageId == null) {
            return Optional.empty();
        }
        return llmCallRepo.findLatestByJobStepAndStage(jobId, PromptName.SCENE_ANALYSIS.promptKey(), stageId);
    }
}
