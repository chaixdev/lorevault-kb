package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.TriadAnalysisArtifactLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GraphTriadAnalysisArtifactLookup implements TriadAnalysisArtifactLookup {

    private final IngestionJobGraphRepository ingestionJobGraphRepository;
    private final StatusRecordGraphRepository statusRecordGraphRepository;
    private final LlmCallRecordGraphRepository llmCallRecordGraphRepository;

    public GraphTriadAnalysisArtifactLookup(IngestionJobGraphRepository ingestionJobGraphRepository,
                                            StatusRecordGraphRepository statusRecordGraphRepository,
                                            LlmCallRecordGraphRepository llmCallRecordGraphRepository) {
        this.ingestionJobGraphRepository = ingestionJobGraphRepository;
        this.statusRecordGraphRepository = statusRecordGraphRepository;
        this.llmCallRecordGraphRepository = llmCallRecordGraphRepository;
    }

    @Override
    public Optional<UUID> findLatestJobIdByChapterId(UUID chapterId) {
        if (chapterId == null) {
            return Optional.empty();
        }
        return ingestionJobGraphRepository.findLatestJobIdByChapterId(chapterId);
    }

    @Override
    public Optional<StatusRecord> findLatestTriadStatusByCurrentSceneId(UUID jobId, UUID currentSceneId) {
        if (jobId == null || currentSceneId == null) {
            return Optional.empty();
        }
        return statusRecordGraphRepository.findLatestTriadStatusByCurrentSceneId(jobId, currentSceneId.toString());
    }

    @Override
    public Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID statusRecordId) {
        if (jobId == null || statusRecordId == null) {
            return Optional.empty();
        }
        return llmCallRecordGraphRepository.findLatestByJobStepAndStatusRecord(jobId, "scene-analysis", statusRecordId);
    }
}
