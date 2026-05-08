package com.lorevault.api.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lorevault.api.cli.step.StepCatalog;
import com.lorevault.api.cli.step.StepKey;
import com.lorevault.api.cli.step.StepOrchestrator;
import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.UUID;
import java.util.concurrent.Callable;

@Component
@Command(
        name = "step",
        description = "Pipeline step execution",
        subcommands = {
                StepCommand.RunCommand.class,
                StepCommand.ListCommand.class
        }
)
@Slf4j
public class StepCommand implements Callable<Integer> {

    private final ObjectMapper objectMapper;

    public StepCommand(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() {
        System.out.println("Usage: step <run|list>");
        return 0;
    }

    @Component
    @Command(name = "run", description = "Run a single pipeline step")
    static class RunCommand implements Callable<Integer> {

        @ParentCommand
        StepCommand parent;

        private final StepOrchestrator stepOrchestrator;

        RunCommand(StepOrchestrator stepOrchestrator) {
            this.stepOrchestrator = stepOrchestrator;
        }

        @Parameters(index = "0", description = "Step name (e.g., SCENE_DETECTION)")
        StepKey stepKey;

        @Option(names = {"--job"}, description = "Ingestion job ID (UUID)", required = true)
        UUID jobId;

        @Option(names = {"--chapter"}, description = "Chapter ID (UUID)", required = true)
        UUID chapterId;

        @Override
        public Integer call() {
            log.info("Running step {} for job={} chapter={}", stepKey, jobId, chapterId);
            try {
                StepResult result = stepOrchestrator.runStep(stepKey, jobId, chapterId);

                ObjectNode output = parent.objectMapper.createObjectNode();
                output.put("step", stepKey.name());
                output.put("success", result.success());
                output.put("summary", result.summary());
                output.put("durationMs", result.durationMs());
                output.put("retryable", result.retryable());
                if (!result.counts().isEmpty()) {
                    ObjectNode counts = output.putObject("counts");
                    result.counts().forEach(counts::put);
                }

                System.out.println(parent.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
                return result.success() ? 0 : 1;
            } catch (IllegalArgumentException e) {
                System.err.printf("Error: %s%n", e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.printf("Error running step %s: %s%n", stepKey, e.getMessage());
                log.error("Step execution failed", e);
                return 1;
            }
        }
    }

    @Component
    @Command(name = "list", description = "List available pipeline steps")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand
        StepCommand parent;

        private final StepCatalog stepCatalog;

        ListCommand(StepCatalog stepCatalog) {
            this.stepCatalog = stepCatalog;
        }

        @Override
        public Integer call() {
            var steps = stepCatalog.all();
            try {
                ObjectNode output = parent.objectMapper.createObjectNode();
                output.put("count", steps.size());
                var stepsArray = output.putArray("steps");
                steps.forEach((key, def) -> {
                    var stepObj = stepsArray.addObject();
                    stepObj.put("key", key.name());
                    stepObj.put("description", def.description());
                    var prereqs = stepObj.putArray("prerequisites");
                    def.prerequisites().forEach(p -> prereqs.add(p.name()));
                });

                System.out.println(parent.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
                return 0;
            } catch (Exception e) {
                System.err.printf("Error: %s%n", e.getMessage());
                return 1;
            }
        }
    }
}