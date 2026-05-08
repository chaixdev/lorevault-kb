package com.lorevault.api.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lorevault.api.ingestion.job.PaginatedJobSummaries;
import com.lorevault.api.ingestion.submission.IngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Component
@Command(
        name = "jobs",
        description = "List and inspect ingestion jobs",
        subcommands = {JobsCommand.ListCommand.class}
)
@Slf4j
public class JobsCommand implements Callable<Integer> {

    private final ObjectMapper objectMapper;

    public JobsCommand(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() {
        System.out.println("Usage: jobs <list>");
        return 0;
    }

    @Component
    @Command(name = "list", description = "List ingestion jobs")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand
        JobsCommand parent;

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

                ObjectNode output = parent.objectMapper.createObjectNode();
                output.put("total", result.pagination().total());
                output.put("offset", result.pagination().offset());
                output.put("hasMore", result.pagination().hasMore());

                ArrayNode jobsArray = output.putArray("jobs");
                for (var job : result.jobs()) {
                    ObjectNode jobNode = jobsArray.addObject();
                    jobNode.put("jobId", job.jobId().toString());
                    jobNode.put("chapterId", job.chapterId().toString());
                    jobNode.put("bookId", job.bookId().toString());
                    jobNode.put("chapterTitle", job.chapterTitle());
                    jobNode.put("status", job.status().name());
                    jobNode.put("progress", job.progress());
                    if (job.createdAt() != null) {
                        jobNode.put("createdAt", job.createdAt().toString());
                    }
                }

                System.out.println(parent.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
                return 0;
            } catch (Exception e) {
                System.err.printf("Error: %s%n", e.getMessage());
                return 1;
            }
        }
    }
}