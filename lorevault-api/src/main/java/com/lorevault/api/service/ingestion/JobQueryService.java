package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.IngestionJobNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for querying and listing ingestion jobs.
 * Handles job status retrieval, filtering, pagination, and formatting.
 * Extracted from IngestionService to improve single responsibility and testability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobQueryService {

    private final ContentPersistencePort contentPersistencePort;

    /**
     * Context object for job filtering parameters
     */
    public static class JobFilterContext {
        private final String universe;
        private final String status;
        private final int limit;
        private final int offset;

        public JobFilterContext(String universe, String status, int limit, int offset) {
            this.universe = universe;
            this.status = status;
            this.limit = limit;
            this.offset = offset;
        }

        public String getUniverse() { return universe; }
        public String getStatus() { return status; }
        public int getLimit() { return limit; }
        public int getOffset() { return offset; }

        public boolean hasUniverseFilter() {
            return universe != null && !universe.isBlank();
        }

        public boolean hasStatusFilter() {
            return status != null && !status.isBlank();
        }
    }

    /**
     * Get the status of an ingestion job
     */
    public Optional<JobStatusResponse> getJobStatus(UUID jobId) {
        try {
            Optional<IngestionJobNode> jobNodeOpt = contentPersistencePort.findJob(jobId);
            if (jobNodeOpt.isEmpty()) {
                return Optional.empty();
            }

            IngestionJobNode jobNode = jobNodeOpt.get();
            return Optional.of(buildJobStatusResponse(jobNode, jobId));
            
        } catch (Exception e) {
            log.warn("Graph job lookup failed for job {}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * List jobs with optional universe and status filters and pagination
     */
    public JobListResponse listJobs(String universe, String status, int limit, int offset) {
        JobFilterContext filterContext = new JobFilterContext(universe, status, limit, offset);
        
        try {
            List<IngestionJobNode> allJobs = loadJobsWithUniverseFilter(filterContext);
            List<IngestionJobNode> filteredJobs = applyStatusFilter(allJobs, filterContext);
            List<IngestionJobNode> sortedJobs = sortJobsByCreatedDate(filteredJobs);
            
            return buildPaginatedResponse(sortedJobs, filterContext);
            
        } catch (Exception e) {
            log.debug("Graph listJobs error: {}", e.getMessage());
            return new JobListResponse(List.of(), new JobListResponse.Pagination(0, limit, offset, false));
        }
    }

    private JobStatusResponse buildJobStatusResponse(IngestionJobNode jobNode, UUID jobId) {
        List<JobStatusResponse.StatusUpdateDto> recentUpdates = loadRecentStatusUpdates(jobId);
        
        JobStatusResponse response = new JobStatusResponse();
        response.setJobId(jobNode.getId());
        response.setChapterId(jobNode.getChapterId());
        response.setCreatedAt(jobNode.getCreatedAt());
        response.setCompletedAt(jobNode.getCompletedAt());
        response.setRecentUpdates(recentUpdates);

        // Set current status information
        var currentStatus = jobNode.getCurrentStatusRecord();
        if (currentStatus != null) {
            response.setCurrentStatus(currentStatus.getStatus());
            response.setProgressPercent(currentStatus.getProgressPercent());
            response.setIsComplete(currentStatus.getStatus().isTerminal());
        }

        return response;
    }

    private List<JobStatusResponse.StatusUpdateDto> loadRecentStatusUpdates(UUID jobId) {
        try {
            var recentNodes = contentPersistencePort.findStatusHistoryForJob(jobId);
            return recentNodes.stream()
                    .map(node -> new JobStatusResponse.StatusUpdateDto(
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

    private List<IngestionJobNode> loadJobsWithUniverseFilter(JobFilterContext filterContext) {
        if (filterContext.hasUniverseFilter()) {
            List<ChapterNode> chapters = contentPersistencePort.findChaptersByUniverse(filterContext.getUniverse());
            List<UUID> chapterIds = chapters.stream().map(ChapterNode::getId).toList();
            return contentPersistencePort.findJobsByChapterIds(chapterIds);
        } else {
            return contentPersistencePort.findAllJobs();
        }
    }

    private List<IngestionJobNode> applyStatusFilter(List<IngestionJobNode> jobs, JobFilterContext filterContext) {
        if (!filterContext.hasStatusFilter()) {
            return jobs;
        }

        StatusFilterStrategy filterStrategy = createStatusFilterStrategy(filterContext.getStatus());
        
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

    private List<IngestionJobNode> sortJobsByCreatedDate(List<IngestionJobNode> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt()); // Newest first
                })
                .toList();
    }

    private JobListResponse buildPaginatedResponse(List<IngestionJobNode> sortedJobs, JobFilterContext filterContext) {
        long total = sortedJobs.size();
        int from = Math.min(filterContext.getOffset(), sortedJobs.size());
        int to = Math.min(from + filterContext.getLimit(), sortedJobs.size());
        
        List<IngestionJobNode> pageSlice = sortedJobs.subList(from, to);
        List<JobListResponse.JobSummary> summaries = buildJobSummaries(pageSlice);
        
        boolean hasMore = (long) (filterContext.getOffset() + filterContext.getLimit()) < total;
        JobListResponse.Pagination pagination = new JobListResponse.Pagination(
                total, filterContext.getLimit(), filterContext.getOffset(), hasMore);
        
        return new JobListResponse(summaries, pagination);
    }

    private List<JobListResponse.JobSummary> buildJobSummaries(List<IngestionJobNode> jobs) {
        List<JobListResponse.JobSummary> summaries = new ArrayList<>();
        
        for (IngestionJobNode jobNode : jobs) {
            JobListResponse.JobSummary summary = new JobListResponse.JobSummary();
            summary.setJobId(jobNode.getId());
            summary.setChapterId(jobNode.getChapterId());
            summary.setCreatedAt(jobNode.getCreatedAt());
            summary.setCompletedAt(jobNode.getCompletedAt());

            // Set current status and progress
            var currentStatus = jobNode.getCurrentStatusRecord();
            if (currentStatus != null) {
                summary.setStatus(currentStatus.getStatus());
                summary.setProgress(currentStatus.getProgressPercent());
            }

            // Enrich with chapter information
            enrichSummaryWithChapterInfo(summary, jobNode.getChapterId());
            
            summaries.add(summary);
        }
        
        return summaries;
    }

    private void enrichSummaryWithChapterInfo(JobListResponse.JobSummary summary, UUID chapterId) {
        try {
            contentPersistencePort.findChapterById(chapterId).ifPresent(chapter -> {
                summary.setChapterTitle(chapter.getChapterTitle());
                summary.setUniverse(chapter.getUniverse());
                summary.setSeries(chapter.getSeries());
                summary.setBookNumber(chapter.getBookNumber());
                summary.setChapterNumber(chapter.getChapterNumber());
            });
        } catch (Exception e) {
            log.debug("Failed to enrich job summary with chapter info for chapter {}: {}", chapterId, e.getMessage());
        }
    }

    // Strategy pattern for different status filtering approaches
    private interface StatusFilterStrategy {
        boolean matches(IngestionJobNode job);
    }

    private static class ActiveJobsFilterStrategy implements StatusFilterStrategy {
        private static final List<IngestionStatus> TERMINAL_STATUSES = List.of(IngestionStatus.COMPLETE, IngestionStatus.FAILED);

        @Override
        public boolean matches(IngestionJobNode job) {
            var currentStatus = job.getCurrentStatusRecord();
            return currentStatus == null || !TERMINAL_STATUSES.contains(currentStatus.getStatus());
        }
    }

    private static class SpecificStatusFilterStrategy implements StatusFilterStrategy {
        private final IngestionStatus targetStatus;

        public SpecificStatusFilterStrategy(IngestionStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        @Override
        public boolean matches(IngestionJobNode job) {
            var currentStatus = job.getCurrentStatusRecord();
            return currentStatus != null && targetStatus.equals(currentStatus.getStatus());
        }
    }
}
