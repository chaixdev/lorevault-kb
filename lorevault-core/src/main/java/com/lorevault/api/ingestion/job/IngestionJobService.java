package com.lorevault.api.ingestion.job;

import com.lorevault.api.content.scene.Scene;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.chunk.ChunkGraphRepository;
import com.lorevault.api.content.scene.SceneGraphRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consolidated service for managing ingestion jobs.
 * Handles both job lifecycle operations (creation, status updates, completion)
 * and query operations (status retrieval, filtering, pagination).
 * 
 * Consolidates functionality from IngestionJobLifecycleService and JobQueryService
 * to eliminate artificial service boundaries and simplify the codebase.
 */
@Service
@Slf4j
public class IngestionJobService {

    private final ChunkGraphRepository chunkRepo;
    private final SceneGraphRepository sceneRepo;
    private final ChapterGraphRepository chapterRepo;
    private final IngestionJobGraphRepository jobRepo;
    private final StatusRecordGraphRepository statusRepo;

    public IngestionJobService(
        ChunkGraphRepository chunkRepo,
        SceneGraphRepository sceneRepo,
        ChapterGraphRepository chapterRepo,
        IngestionJobGraphRepository jobRepo,
        StatusRecordGraphRepository statusRepo
    ) {
        this.chunkRepo = chunkRepo;
        this.sceneRepo = sceneRepo;
        this.chapterRepo = chapterRepo;
        this.jobRepo = jobRepo;
        this.statusRepo = statusRepo;
    }

    // ================================
    // JOB LIFECYCLE OPERATIONS
    // ================================

    /**
     * Create a new ingestion job and initial status record
     */
    @Transactional
    public IngestionJob createIngestionJob(UUID chapterId) {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setChapterId(chapterId);
        job.setCreatedAt(LocalDateTime.now());

        IngestionJob persistedJob = jobRepo.save(job);

        StatusRecord initialStatus = createInitialStatusRecord(persistedJob.getId(), chapterId);

        statusRepo.save(initialStatus);
        jobRepo.swapCurrentStatus(persistedJob.getId(), initialStatus.getId());
        
        persistedJob.setCurrentStatus(initialStatus);
        return persistedJob;
    }

    /**
     * Mark job as completed successfully
     */
    @Transactional
    public void completeJob(IngestionJob job, UUID chapterId, int chapterLength) {
        int via = chunkRepo.countByChapterIdViaScenes(chapterId);
        int chunkCount = via > 0 ? via : chunkRepo.countByChapterId(chapterId);
        
        StatusRecord completionStatus = new StatusRecord(
                UUID.randomUUID(),
                job.getId(),
                LocalDateTime.now(),
                IngestionStatus.COMPLETE,
                String.format("Chapter processing completed successfully. Created %d chunks.", chunkCount),
                100, // progressPercent
                Map.of(
                    "version", "0.2.0",
                    "pipeline", "content_segmentation",
                    "chunkCount", String.valueOf(chunkCount),
                    "chapterLength", String.valueOf(chapterLength),
                    "completedAt", LocalDateTime.now().toString()
                )
        );

        job.setCurrentStatus(completionStatus);
        job.setCompletedAt(LocalDateTime.now());
        saveStatusRecord(completionStatus);
        updateJobCompletedAt(job.getId(), job.getCompletedAt());
        
        log.info("Job {} completed successfully with {} chunks", job.getId(), chunkCount);
    }

    /**
     * Mark job as failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJob(IngestionJob job, String errorMessage) {
        StatusRecord failureStatus = new StatusRecord(
                UUID.randomUUID(), 
                job.getId(), 
                LocalDateTime.now(), 
                IngestionStatus.FAILED, 
                errorMessage,
                0, // progressPercent - failed jobs have 0 progress
                Map.of(
                    "version", "0.2.0", 
                    "failedAt", LocalDateTime.now().toString()
                )
        );
        
        job.setCurrentStatus(failureStatus);
        job.setCompletedAt(LocalDateTime.now());
        saveStatusRecord(failureStatus);
        updateJobCompletedAt(job.getId(), job.getCompletedAt());
        
        log.error("Job {} failed: {}", job.getId(), errorMessage);
    }

    /**
     * Mark job as failed and clean up any partially processed data
     * This allows a clean retry of the chapter later
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJobWithCleanup(IngestionJob job, String errorMessage) {
        UUID chapterId = job.getChapterId();
        
        // Clean up partially processed data
        int viaCount = chunkRepo.countByChapterIdViaScenes(chapterId);
        int deletedChunks;
        if (viaCount > 0) {
            chunkRepo.deleteByChapterIdViaScenes(chapterId);
            deletedChunks = viaCount;
        } else {
            int legacyCount = chunkRepo.countByChapterId(chapterId);
            if (legacyCount > 0) {
                chunkRepo.deleteByChapterId(chapterId);
            }
            deletedChunks = legacyCount;
        }
        log.info("Cleaned up {} chunks for failed chapter {} (graph)", deletedChunks, chapterId);
        
        List<Scene> existingScenes = sceneRepo.findByChapterId(chapterId);
        sceneRepo.deleteByChapterId(chapterId);
        int deletedScenes = existingScenes.size();
        log.info("Cleaned up {} scenes for failed chapter {} (graph)", deletedScenes, chapterId);
        
        failJob(job, errorMessage + " (data cleaned up for retry)");
    }

    /**
     * Update job status with new status record
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobStatus(UUID jobId, IngestionStatus status, String description, Map<String, Object> properties) {
        StatusRecord statusRecord = new StatusRecord(
            UUID.randomUUID(),
            jobId,
            LocalDateTime.now(),
            status,
            description,
            status.getProgressPercentage(),
            stringifyProperties(properties)
        );
        
        try {
            statusRepo.save(statusRecord);
            jobRepo.swapCurrentStatus(jobId, statusRecord.getId());
            log.debug("Created status record for job {}: {} - {}", jobId, status, description);
        } catch (Exception e) {
            log.debug("Failed to add status record to graph: {}", e.getMessage());
        }
    }

    // ================================
    // JOB QUERY OPERATIONS
    // ================================

    /**
     * Get the status of an ingestion job
     */
    public Optional<JobStatusDetails> getJobStatus(UUID jobId) {
        try {
            Optional<IngestionJob> jobOpt = jobRepo.findById(jobId);
            if (jobOpt.isEmpty()) {
                return Optional.empty();
            }

            IngestionJob job = jobOpt.get();
            return Optional.of(buildJobStatusDetails(job, jobId));
            
        } catch (Exception e) {
            log.warn("Graph job lookup failed for job {}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * List jobs with optional universe and status filters and pagination
     */
    public PaginatedJobSummaries listJobs(String universe, String status, int limit, int offset) {
        JobFilterContext filterContext = new JobFilterContext(universe, status, limit, offset);
        
        try {
            List<IngestionJob> allJobs = loadJobsWithUniverseFilter(filterContext);
            List<IngestionJob> filteredJobs = applyStatusFilter(allJobs, filterContext);
            List<IngestionJob> sortedJobs = sortJobsByCreatedDate(filteredJobs);
            
            return buildPaginatedResponse(sortedJobs, filterContext);
            
        } catch (Exception e) {
            log.debug("Graph listJobs error: {}", e.getMessage());
            return new PaginatedJobSummaries(List.of(), new PaginatedJobSummaries.Pagination(0, limit, offset, false));
        }
    }

    // ================================
    // PRIVATE HELPER METHODS
    // ================================

    private StatusRecord createInitialStatusRecord(UUID jobId, UUID chapterId) {
        return new StatusRecord(
                UUID.randomUUID(),
                jobId,
                LocalDateTime.now(),
                IngestionStatus.QUEUED,
                "Chapter submitted and queued for processing",
                0, // progressPercent
                Map.of("chapterId", chapterId.toString())
        );
    }

    private Map<String, String> stringifyProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }

        return properties.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() == null ? "" : e.getValue().toString()
                ));
    }

    private void saveStatusRecord(StatusRecord statusRecord) {
        try {
            statusRepo.save(statusRecord);
            jobRepo.swapCurrentStatus(statusRecord.getJobId(), statusRecord.getId());
            log.debug("Created status record for job {}: {} - {}", 
                    statusRecord.getJobId(), statusRecord.getStatus(), statusRecord.getStepDescription());
        } catch (Exception e) {
            log.debug("Failed to add status record to graph: {}", e.getMessage());
        }
    }

    private void updateJobCompletedAt(UUID jobId, LocalDateTime completedAt) {
        try {
            jobRepo.findById(jobId).ifPresent(job -> {
                job.setCompletedAt(completedAt);
                jobRepo.save(job);
            });
        } catch (Exception e) {
            log.debug("Graph completion update failed for job {}: {}", jobId, e.getMessage());
        }
    }

    private JobStatusDetails buildJobStatusDetails(IngestionJob job, UUID jobId) {
        List<JobStatusDetails.StatusUpdate> recentUpdates = loadRecentStatusUpdates(jobId);
        
        UUID bookId = null;
        if (job.getChapterId() != null) {
            Optional<Chapter> chapterOpt = chapterRepo.findById(job.getChapterId());
            if (chapterOpt.isPresent()) {
                bookId = chapterOpt.get().getBookId();
            }
        }

        IngestionStatus currentStatus = null;
        int progressPercent = 0;
        boolean isComplete = false;
        JobStatusDetails.FailureDetails failureDetails = null;

        var statusRecord = job.getCurrentStatus();
        if (statusRecord != null) {
            currentStatus = statusRecord.getStatus();
            progressPercent = statusRecord.getProgressPercent();
            isComplete = statusRecord.getStatus().isTerminal();
            failureDetails = extractFailureDetails(statusRecord);
        }

        return new JobStatusDetails(
            job.getId(),
            job.getChapterId(),
            bookId,
            currentStatus,
            progressPercent,
            isComplete,
            job.getCreatedAt(),
            job.getCompletedAt(),
            recentUpdates,
            failureDetails
        );
    }

    private JobStatusDetails.FailureDetails extractFailureDetails(StatusRecord statusRecord) {
        if (statusRecord == null || statusRecord.getStatus() != IngestionStatus.FAILED) {
            return null;
        }

        Map<String, String> properties = statusRecord.getProperties();
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        Map<String, String> details = properties.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith("failureDetail."))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring("failureDetail.".length()),
                        Map.Entry::getValue,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));

        if (!properties.containsKey("failureCode")
                && !properties.containsKey("failureMessage")
                && !properties.containsKey("failureExceptionType")
                && !properties.containsKey("failureStage")
                && details.isEmpty()) {
            return null;
        }

        return new JobStatusDetails.FailureDetails(
                properties.get("failureCode"),
                properties.get("failureMessage"),
                properties.get("failureExceptionType"),
                properties.get("failureStage"),
                details
        );
    }

    private List<JobStatusDetails.StatusUpdate> loadRecentStatusUpdates(UUID jobId) {
        try {
            var recentNodes = statusRepo.findStatusHistoryForJob(jobId);
            return recentNodes.stream()
                    .map(node -> new JobStatusDetails.StatusUpdate(
                            node.getStatus(), 
                            node.getStepDescription(), 
                            node.getTimestamp(), 
                            node.getProgressPercent()))
                    .toList();
        } catch (Exception e) {
            log.debug("Failed to load status history for job {}: {}", jobId, e.getMessage());
            return List.of();
        }
    }

    private List<IngestionJob> loadJobsWithUniverseFilter(JobFilterContext filterContext) {
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

    private List<IngestionJob> applyStatusFilter(List<IngestionJob> jobs, JobFilterContext filterContext) {
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

    private List<IngestionJob> sortJobsByCreatedDate(List<IngestionJob> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt()); // Newest first
                })
                .toList();
    }

    private PaginatedJobSummaries buildPaginatedResponse(List<IngestionJob> sortedJobs, JobFilterContext filterContext) {
        long total = sortedJobs.size();
        int from = Math.min(filterContext.offset(), sortedJobs.size());
        int to = Math.min(from + filterContext.limit(), sortedJobs.size());
        
        List<IngestionJob> pageSlice = sortedJobs.subList(from, to);
        List<JobSummary> summaries = buildJobSummaries(pageSlice);
        
        boolean hasMore = (long) (filterContext.offset() + filterContext.limit()) < total;
        PaginatedJobSummaries.Pagination pagination = new PaginatedJobSummaries.Pagination(
                total, filterContext.limit(), filterContext.offset(), hasMore);
        
        return new PaginatedJobSummaries(summaries, pagination);
    }

    private List<JobSummary> buildJobSummaries(List<IngestionJob> jobs) {
        List<JobSummary> summaries = new ArrayList<>();
        
        for (IngestionJob job : jobs) {
            IngestionStatus status = null;
            int progress = 0;

            // Set current status and progress
            var currentStatus = job.getCurrentStatus();
            if (currentStatus != null) {
                status = currentStatus.getStatus();
                progress = currentStatus.getProgressPercent();
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
                job.getCompletedAt()
            ));
        }
        
        return summaries;
    }

    // ================================
    // HELPER CLASSES
    // ================================

    /**
         * Context object for job filtering parameters
         */
        public record JobFilterContext(String universe, String status, int limit, int offset) {

        public boolean hasUniverseFilter() {
                return universe != null && ! universe.isBlank();
            }

            public boolean hasStatusFilter() {
                return status != null && ! status.isBlank();
            }
        }

    // Strategy pattern for different status filtering approaches
    private interface StatusFilterStrategy {
        boolean matches(IngestionJob job);
    }

    private static class ActiveJobsFilterStrategy implements StatusFilterStrategy {
        private static final List<IngestionStatus> TERMINAL_STATUSES = List.of(IngestionStatus.COMPLETE, IngestionStatus.FAILED);

        @Override
        public boolean matches(IngestionJob job) {
            var currentStatus = job.getCurrentStatus();
            return currentStatus == null || !TERMINAL_STATUSES.contains(currentStatus.getStatus());
        }
    }

    private record SpecificStatusFilterStrategy(IngestionStatus targetStatus) implements StatusFilterStrategy {

        @Override
            public boolean matches(IngestionJob job) {
                var currentStatus = job.getCurrentStatus();
                return currentStatus != null && targetStatus.equals(currentStatus.getStatus());
            }
        }
}
