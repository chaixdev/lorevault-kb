package com.lorevault.api.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lorevault.api.ingestion.submission.IngestionService;
import com.lorevault.api.ingestion.submission.IngestionSubmissionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Prepares a chapter for step-by-step ingestion.
 *
 * <p>Creates the chapter and an ingestion job, but does <em>not</em> trigger
 * the async pipeline. Use {@code step run} to execute individual steps.
 *
 * <p>Chapter text is read from a file path or stdin (use {@code -} for stdin).
 */
@Component
@Command(
        name = "prepare",
        description = "Prepare a chapter for step-by-step ingestion",
        mixinStandardHelpOptions = true
)
@Slf4j
public class PrepareCommand implements Callable<Integer> {

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public PrepareCommand(IngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Option(names = {"-b", "--book"}, description = "Book UUID", required = true)
    UUID bookId;

    @Option(names = {"-n", "--chapter-number"}, description = "Chapter number", required = true)
    Integer chapterNumber;

    @Option(names = {"-t", "--title"}, description = "Chapter title", defaultValue = "")
    String chapterTitle;

    @Parameters(index = "0", description = "Chapter text file path, or '-' for stdin")
    String source;

    @Override
    public Integer call() {
        try {
            String chapterText = readText(source);
            if (chapterText.isBlank()) {
                System.err.println("Error: chapter text is empty");
                return 1;
            }

            IngestionSubmissionResult result = ingestionService.prepareChapter(
                    bookId, chapterNumber, chapterTitle, chapterText);

            ObjectNode output = objectMapper.createObjectNode();
            output.put("jobId", result.jobId().toString());
            output.put("chapterId", result.chapterId().toString());

            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.printf("Error: %s%n", e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.printf("Error: %s%n", e.getMessage());
            log.error("Prepare failed", e);
            return 1;
        }
    }

    private String readText(String source) throws IOException {
        if ("-".equals(source)) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return Files.readString(Path.of(source), StandardCharsets.UTF_8);
    }
}