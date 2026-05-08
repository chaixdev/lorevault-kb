package com.lorevault.api.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lorevault.api.ingestion.job.JobStatusDetails;
import com.lorevault.api.ingestion.submission.IngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.UUID;
import java.util.concurrent.Callable;

@Component
@Command(
        name = "status",
        description = "Show ingestion job status",
        mixinStandardHelpOptions = true
)
@Slf4j
public class StatusCommand implements Callable<Integer> {

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public StatusCommand(IngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
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
            ObjectNode output = objectMapper.createObjectNode();
            output.put("jobId", details.jobId().toString());
            output.put("chapterId", details.chapterId().toString());
            output.put("bookId", details.bookId().toString());
            output.put("status", details.currentStatus().name());
            output.put("progress", details.progressPercent());
            output.put("complete", details.isComplete());
            output.put("createdAt", details.createdAt().toString());
            if (details.completedAt() != null) {
                output.put("completedAt", details.completedAt().toString());
            }
            if (details.failureDetails() != null) {
                ObjectNode failure = output.putObject("failure");
                failure.put("code", details.failureDetails().code());
                failure.put("message", details.failureDetails().message());
            }

            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
            return 0;
        } catch (Exception e) {
            System.err.printf("Error: %s%n", e.getMessage());
            log.error("Status lookup failed", e);
            return 1;
        }
    }
}