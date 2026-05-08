package com.lorevault.api.cli.command;

import com.lorevault.api.ingestion.job.JobStatusDetails;
import com.lorevault.api.ingestion.job.PaginatedJobSummaries;
import com.lorevault.api.ingestion.submission.IngestionService;
import com.lorevault.api.ingestion.submission.IngestionSubmissionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.UUID;
import java.util.concurrent.Callable;

@Component
@Command(
        name = "ingest",
        description = "Chapter ingestion: prepare, status, list",
        subcommands = {
                IngestCommand.PrepareCommand.class,
                IngestCommand.StatusCommand.class,
                IngestCommand.ListCommand.class
        }
)
@Slf4j
public class IngestCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: ingest <prepare|status|list>");
        return 0;
    }

    @Component
    @Command(name = "prepare", description = "Prepare a chapter for step-by-step ingestion (creates job, no pipeline trigger)")
    static class PrepareCommand implements Callable<Integer> {

        @ParentCommand
        IngestCommand parent;

        private final IngestionService ingestionService;

        PrepareCommand(IngestionService ingestionService) {
            this.ingestionService = ingestionService;
        }

        @Parameters(index = "0", description = "Book ID (UUID)")
        UUID bookId;

        @Parameters(index = "1", description = "Chapter number")
        Integer chapterNumber;

        @Option(names = {"--title"}, description = "Chapter title", defaultValue = "")
        String chapterTitle;

        @Option(names = {"--text"}, description = "Chapter text (use @file.txt to read from file)", required = true)
        String chapterText;

        @Override
        public Integer call() {
            log.info("Preparing chapter: bookId={}, chapterNumber={}", bookId, chapterNumber);
            try {
                IngestionSubmissionResult result = ingestionService.prepareChapter(bookId, chapterNumber, chapterTitle, chapterText);
                System.out.printf("Prepared chapter for step-by-step ingestion%n");
                System.out.printf("  jobId     = %s%n", result.jobId());
                System.out.printf("  chapterId = %s%n", result.chapterId());
                System.out.printf("%nNext: run-step scene-detection --job %s --chapter %s%n", result.jobId(), result.chapterId());
                return 0;
            } catch (Exception e) {
                System.err.printf("Error: %s%n", e.getMessage());
                log.error("Prepare failed", e);
                return 1;
            }
        }
    }

    @Component
    @Command(name = "status", description = "Show ingestion job status")
    static class StatusCommand implements Callable<Integer> {

        @ParentCommand
        IngestCommand parent;

        private final IngestionService ingestionService;

        StatusCommand(IngestionService ingestionService) {
            this.ingestionService = ingestionService;
        }

        @Parameters(index = "0", description = "Job ID (UUID)")
        UUID jobId;

        @Override
        public Integer call() {
            try {
                var status = ingestionService.getJobStatus(jobId);
                if (status.isEmpty()) {
                    System.err.printf("Job not found: %s%n", jobId);
                    return 1;
                }
                JobStatusDetails details = status.get();
                System.out.printf("Job: %s%n", details.jobId());
                System.out.printf("  Status:    %s%n", details.currentStatus());
                System.out.printf("  Progress:  %d%%%n", details.progressPercent());
                System.out.printf("  Complete:  %s%n", details.isComplete());
                System.out.printf("  Created:   %s%n", details.createdAt());
                if (details.completedAt() != null) {
                    System.out.printf("  Completed: %s%n", details.completedAt());
                }
                if (details.failureDetails() != null) {
                    System.out.printf("  Failure:   %s%n", details.failureDetails().message());
                }
                return 0;
            } catch (Exception e) {
                System.err.printf("Error: %s%n", e.getMessage());
                return 1;
            }
        }
    }

    @Component
    @Command(name = "list", description = "List ingestion jobs")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand
        IngestCommand parent;

        private final IngestionService ingestionService;

        ListCommand(IngestionService ingestionService) {
            this.ingestionService = ingestionService;
        }

        @Option(names = {"--universe"}, description = "Filter by universe", defaultValue = "")
        String universe;

        @Option(names = {"--status"}, description = "Filter by status", defaultValue = "")
        String statusFilter;

        @Option(names = {"--limit"}, description = "Max results", defaultValue = "20")
        int limit;

        @Option(names = {"--offset"}, description = "Offset", defaultValue = "0")
        int offset;

        @Override
        public Integer call() {
            try {
                PaginatedJobSummaries result = ingestionService.listJobs(universe, statusFilter, limit, offset);
                System.out.printf("Jobs (%d of %d, offset=%d):%n", result.jobs().size(), result.pagination().total(), offset);
                for (var job : result.jobs()) {
                    System.out.printf("  %s  chapter=%s  status=%s  progress=%d%%%n",
                            job.jobId(), job.chapterId(), job.status(), job.progress());
                }
                if (result.pagination().hasMore()) {
                    System.out.printf("%nMore results available. Use --offset %d%n", offset + limit);
                }
                return 0;
            } catch (Exception e) {
                System.err.printf("Error: %s%n", e.getMessage());
                return 1;
            }
        }
    }
}