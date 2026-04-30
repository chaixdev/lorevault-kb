package com.lorevault.api.ingestion.job;

import java.util.List;

public record PaginatedJobSummaries(
    List<JobSummary> jobs,
    Pagination pagination
) {
    public record Pagination(
        long total,
        int limit,
        int offset,
        boolean hasMore
    ) {}
}
