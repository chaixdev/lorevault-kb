package com.lorevault.api.ingestion.job;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.ingestion.orchestration.IngestionPipelineCoordinator;
import com.lorevault.api.ingestion.orchestration.Stage;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing ingestion jobs backed by the durable orchestration model.
 * <p>
 * Job lifecycle is delegated to {@link IngestionPipelineCoordinator} which manages
 * {@link Stage} nodes, DAG transitions, and stage-level status. This service serves
 * as the public API for creating jobs, querying status (derived from the Stage subgraph),
 * and listing jobs with filtering and pagination.
 */
@Service
@Slf4j
public class IngestionJobService {

    private final ChapterGraphRepository chapterRepo;
    private final ChapterIngestionJobGraphRepository jobRepo;
    private final IngestionPipelineCoordinator coordinator;
    private final StageGraphRepository stageRepo;

    public IngestionJobService(
        ChapterGraphRepository chapterRepo,
        ChapterIngestionJobGraphRepository jobRepo,
        IngestionPipelineCoordinator coordinator,
        StageGraphRepository stageRepo
    ) {
        this.chapterRepo = chapterRepo;
        this.jobRepo = jobRepo;
        this.coordinator = coordinator;
        this.stageRepo = stageRepo;
    }

    // ================================
    // JOB LIFECYCLE OPERATIONS
    // ================================

    /**
     * Create a new ingestion job and bootstrap its pipeline DAG.
     * <p>
     * Creates a {@link ChapterIngestionJob} node, persists it, then delegates to
     * {@link IngestionPipelineCoordinator#bootstrapJob} to create all PENDING
     * {@link Stage} nodes, wire DAG edges, and emit triggers for root stages.
     */
    public ChapterIngestionJob createIngestionJob(UUID chapterId) {
        ChapterIngestionJob job = new ChapterIngestionJob();
        job.setId(UUID.randomUUID());
        job.setChapterId(chapterId);
        job.setCreatedAt(LocalDateTime.now());

        ChapterIngestionJob persistedJob = jobRepo.save(job);

        coordinator.bootstrapJob(persistedJob.getId(), chapterId);

        log.info("Created ingestion job {} for chapter {}", persistedJob.getId(), chapterId);
        return persistedJob;
    }

    // ================================
    // JOB QUERY OPERATIONS
    // ================================

    /**
     * Get the status of an ingestion job derived from its Stage subgraph.
     */
    public Optional<JobStatusDetails> getJobStatus(UUID jobId) {
        try {
            Optional<ChapterIngestionJob> jobOpt = jobRepo.findById(jobId);
            if (jobOpt.isEmpty()) {
                return Optional.empty();
            }

            ChapterIngestionJob job = jobOpt.get();
            return Optional.of(buildJobStatusDetails(job));

        } catch (Exception e) {
            log.warn("Graph job lookup failed for job {}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * List jobs with optional universe and status filters and pagination.
     */
    public PaginatedJobSummaries listJobs(String universe, String status, int limit, int offset) {
        JobFilterContext filterContext = new JobFilterContext(universe, status, limit, offset);

        try {
            List<ChapterIngestionJob> allJobs = loadJobsWithUniverseFilter(filterContext);
            List<ChapterIngestionJob> filteredJobs = applyStatusFilter(allJobs, filterContext);
            List<ChapterIngestionJob> sortedJobs = sortJobsByCreatedDate(filteredJobs);

            return buildPaginatedResponse(sortedJobs, filterContext);

        } catch (Exception e) {
            log.debug("Graph listJobs error: {}", e.getMessage());
            return new PaginatedJobSummaries(List.of(), new PaginatedJobSummaries.Pagination(0, limit, offset, false));
        }
    }

    // ================================
    // PRIVATE HELPER METHODS
    // ================================

    private JobStatusDetails buildJobStatusDetails(ChapterIngestionJob job) {
        List<Stage> stages = stageRepo.findByJobId(job.getId());

        UUID bookId = null;
        if (job.getChapterId() != null) {
            Optional<Chapter> chapterOpt = chapterRepo.findById(job.getChapterId());
            if (chapterOpt.isPresent()) {
                bookId = chapterOpt.get().getBookId();
            }
        }

        IngestionStatus currentStatus = computeOverallStatus(stages);
        int progressPercent = computeProgressPercent(stages, currentStatus);
        boolean isComplete = currentStatus == IngestionStatus.COMPLETE || currentStatus == IngestionStatus.FAILED;
        JobStatusDetails.FailureDetails failureDetails = extractFailureDetails(stages);
        LocalDateTime completedAt = findCompletedAt(stages);

        return new JobStatusDetails(
            job.getId(),
            job.getChapterId(),
            bookId,
            currentStatus,
            progressPercent,
            isComplete,
            job.getCreatedAt(),
            completedAt,
            buildStageStatusUpdates(stages),
            failureDetails
        );
    }

    /**
     * Compute the overall {@link IngestionStatus} from the list of {@link Stage} nodes.
     * <ul>
     *   <li>If {@code INGESTION_COMPLETE} is COMPLETED → {@code COMPLETE}</li>
     *   <li>If any stage is FAILED → {@code FAILED}</li>
     *   <li>Otherwise, find the first non-terminal stage in DAG order</li>
     *   <li>If all stages are terminal → {@code COMPLETE}</li>
     * </ul>
     */
    private IngestionStatus computeOverallStatus(List<Stage> stages) {
        Stage completeStage = findStage(stages, StageKey.INGESTION_COMPLETE);
        if (completeStage != null && completeStage.getStatus() == StageStatus.COMPLETED) {
            return IngestionStatus.COMPLETE;
        }

        boolean anyFailed = stages.stream().anyMatch(s -> s.getStatus() == StageStatus.FAILED);
        if (anyFailed) {
            return IngestionStatus.FAILED;
        }

        // Find first non-terminal stage in DAG (enum) order
        for (StageKey key : StageKey.values()) {
            Stage stage = findStage(stages, key);
            if (stage != null && !stage.getStatus().isTerminal()) {
                return mapStageKeyToIngestionStatus(key);
            }
        }

        // All stages are terminal → complete
        return IngestionStatus.COMPLETE;
    }

    /**
     * Compute progress percent from stage completion ratio or derived status.
     */
    private int computeProgressPercent(List<Stage> stages, IngestionStatus currentStatus) {
        if (currentStatus == IngestionStatus.FAILED) {
            return -1;
        }
        if (stages.isEmpty()) {
            return 0;
        }
        long terminalCount = stages.stream().filter(s -> s.getStatus().isTerminal()).count();
        return (int) (terminalCount * 100L / stages.size());
    }

    /**
     * Find the completion timestamp from the INGESTION_COMPLETE stage,
     * falling back to the latest completedAt among all stages.
     */
    private LocalDateTime findCompletedAt(List<Stage> stages) {
        Stage completeStage = findStage(stages, StageKey.INGESTION_COMPLETE);
        if (completeStage != null && completeStage.getCompletedAt() != null) {
            return completeStage.getCompletedAt();
        }
        return stages.stream()
                .filter(s -> s.getCompletedAt() != null)
                .map(Stage::getCompletedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * Build a list of status updates from stage data for API compatibility.
     */
    private List<JobStatusDetails.StatusUpdate> buildStageStatusUpdates(List<Stage> stages) {
        return stages.stream()
                .filter(s -> s.getStatus().isTerminal() || s.getStatus() == StageStatus.RUNNING)
                .map(s -> new JobStatusDetails.StatusUpdate(
                        mapStageKeyToIngestionStatus(s.getStep()),
                        "Stage " + s.getStep().name() + " is " + s.getStatus().name(),
                        s.getCompletedAt() != null ? s.getCompletedAt()
                                : s.getTriggeredAt() != null ? s.getTriggeredAt() : LocalDateTime.now(),
                        computeProgressPercent(stages, computeOverallStatus(stages))))
                .toList();
    }

    /**
     * Extract failure details from the first FAILED stage, if any.
     */
    private JobStatusDetails.FailureDetails extractFailureDetails(List<Stage> stages) {
        Stage failedStage = stages.stream()
                .filter(s -> s.getStatus() == StageStatus.FAILED)
                .findFirst()
                .orElse(null);

        if (failedStage == null) {
            return null;
        }

        return new JobStatusDetails.FailureDetails(
                null, // code
                failedStage.getErrorMessage(),
                null, // exceptionType
                failedStage.getStep().name(),
                Map.of());
    }

    /**
     * Map a {@link StageKey} to its corresponding {@link IngestionStatus}.
     * Book-level reduction stages map to their chapter-level counterparts
     * for coarse-grained job status reporting.
     */
    private IngestionStatus mapStageKeyToIngestionStatus(StageKey key) {
        return switch (key) {
            case SCENE_SEGMENTATION -> IngestionStatus.SCENE_SEGMENTATION;
            case CHUNKING -> IngestionStatus.CHUNKING;
            case EMBEDDING -> IngestionStatus.EMBEDDING_CHUNKS;
            case CHAPTER_INDIVIDUAL_CONSOLIDATION, BOOK_INDIVIDUAL_CONSOLIDATION -> IngestionStatus.RESOLVING_INDIVIDUALS;
            case CHAPTER_COLLECTIVE_CONSOLIDATION, BOOK_COLLECTIVE_CONSOLIDATION -> IngestionStatus.RESOLVING_COLLECTIVES;
            case CHAPTER_LOCATION_CONSOLIDATION, BOOK_LOCATION_CONSOLIDATION -> IngestionStatus.RESOLVING_LOCATIONS;
            case CHAPTER_OBJECT_CONSOLIDATION, BOOK_OBJECT_CONSOLIDATION -> IngestionStatus.RESOLVING_OBJECTS;
            case CHAPTER_EVENT_CONSOLIDATION -> IngestionStatus.EVENT_COREF;
            case CHAPTER_EVENT_EMBEDDING, BOOK_EVENT_CANDIDATE_GENERATION -> IngestionStatus.EVENT_CANDIDATE_GENERATION;
            case INGESTION_COMPLETE -> IngestionStatus.COMPLETE;
        };
    }

    /**
     * Find a stage by its key within a list of stages.
     */
    private Stage findStage(List<Stage> stages, StageKey key) {
        return stages.stream()
                .filter(s -> s.getStep() == key)
                .findFirst()
                .orElse(null);
    }

    private List<ChapterIngestionJob> loadJobsWithUniverseFilter(JobFilterContext filterContext) {
        if (filterContext.hasUniverseFilter()) {
            List<Chapter> chapters = chapterRepo.findAll().stream()
                    .filter(c -> filterContext.universe().equals(c.getUniverse()))
                    .toList();
            List<UUID> chapterIds = chapters.stream().map(Chapter::getId).toList();
            return jobRepo.findByChapterIdIn(chapterIds);
        } else {
            return jobRepo.findAll();
        }
    }

    private List<ChapterIngestionJob> applyStatusFilter(List<ChapterIngestionJob> jobs, JobFilterContext filterContext) {
        if (!filterContext.hasStatusFilter()) {
            return jobs;
        }

        StatusFilterStrategy filterStrategy = createStatusFilterStrategy(filterContext.status());

        return jobs.stream()
                .filter(filterStrategy::matches)
                .toList();
    }

    private StatusFilterStrategy createStatusFilterStrategy(String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return new ActiveJobsFilterStrategy();
        } else {
            return new SpecificStatusFilterStrategy(IngestionStatus.valueOf(status.toUpperCase()));
        }
    }

    private List<ChapterIngestionJob> sortJobsByCreatedDate(List<ChapterIngestionJob> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt()); // Newest first
                })
                .toList();
    }

    private PaginatedJobSummaries buildPaginatedResponse(List<ChapterIngestionJob> sortedJobs, JobFilterContext filterContext) {
        long total = sortedJobs.size();
        int from = Math.min(filterContext.offset(), sortedJobs.size());
        int to = Math.min(from + filterContext.limit(), sortedJobs.size());

        List<ChapterIngestionJob> pageSlice = sortedJobs.subList(from, to);
        List<JobSummary> summaries = buildJobSummaries(pageSlice);

        boolean hasMore = (long) (filterContext.offset() + filterContext.limit()) < total;
        PaginatedJobSummaries.Pagination pagination = new PaginatedJobSummaries.Pagination(
                total, filterContext.limit(), filterContext.offset(), hasMore);

        return new PaginatedJobSummaries(summaries, pagination);
    }

    private List<JobSummary> buildJobSummaries(List<ChapterIngestionJob> jobs) {
        List<JobSummary> summaries = new ArrayList<>();

        for (ChapterIngestionJob job : jobs) {
            IngestionStatus status = null;
            int progress = 0;
            LocalDateTime completedAt = null;

            // Derive status from Stage subgraph
            try {
                List<Stage> stages = stageRepo.findByJobId(job.getId());
                status = computeOverallStatus(stages);
                progress = computeProgressPercent(stages, status);
                completedAt = findCompletedAt(stages);
            } catch (Exception e) {
                log.debug("Failed to load stages for job {}: {}", job.getId(), e.getMessage());
            }

            // Enrich with chapter information
            UUID bookId = null;
            String chapterTitle = null;
            String universe = null;
            String series = null;
            Integer bookNumber = null;
            Integer chapterNumber = null;

            if (job.getChapterId() != null) {
                try {
                    Optional<Chapter> chapterOpt = chapterRepo.findById(job.getChapterId());
                    if (chapterOpt.isPresent()) {
                        Chapter chapter = chapterOpt.get();
                        bookId = chapter.getBookId();
                        chapterTitle = chapter.getChapterTitle();
                        universe = chapter.getUniverse();
                        series = chapter.getSeries();
                        bookNumber = chapter.getBookNumber();
                        chapterNumber = chapter.getChapterNumber();
                    }
                } catch (Exception e) {
                    log.debug("Failed to enrich job summary with chapter info for chapter {}: {}", job.getChapterId(), e.getMessage());
                }
            }

            summaries.add(new JobSummary(
                job.getId(),
                job.getChapterId(),
                bookId,
                chapterTitle,
                universe,
                series,
                bookNumber,
                chapterNumber,
                status,
                progress,
                job.getCreatedAt(),
                completedAt
            ));
        }

        return summaries;
    }

    // ================================
    // HELPER CLASSES
    // ================================

    /**
     * Context object for job filtering parameters.
     */
    public record JobFilterContext(String universe, String status, int limit, int offset) {

        public boolean hasUniverseFilter() {
            return universe != null && !universe.isBlank();
        }

        public boolean hasStatusFilter() {
            return status != null && !status.isBlank();
        }
    }

    // Strategy pattern for different status filtering approaches
    private interface StatusFilterStrategy {
        boolean matches(ChapterIngestionJob job);
    }

    private class ActiveJobsFilterStrategy implements StatusFilterStrategy {
        @Override
        public boolean matches(ChapterIngestionJob job) {
            try {
                List<Stage> stages = stageRepo.findByJobId(job.getId());
                IngestionStatus currentStatus = computeOverallStatus(stages);
                return currentStatus != IngestionStatus.COMPLETE && currentStatus != IngestionStatus.FAILED;
            } catch (Exception e) {
                log.debug("Failed to determine status for job {}: {}", job.getId(), e.getMessage());
                return true; // Assume active if we cannot determine
            }
        }
    }

    private class SpecificStatusFilterStrategy implements StatusFilterStrategy {
        private final IngestionStatus targetStatus;

        SpecificStatusFilterStrategy(IngestionStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        @Override
        public boolean matches(ChapterIngestionJob job) {
            try {
                List<Stage> stages = stageRepo.findByJobId(job.getId());
                return computeOverallStatus(stages) == targetStatus;
            } catch (Exception e) {
                log.debug("Failed to determine status for job {}: {}", job.getId(), e.getMessage());
                return false;
            }
        }
    }
}
