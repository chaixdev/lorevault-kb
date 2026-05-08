package com.lorevault.api.cli.command;

import com.lorevault.api.cli.step.StepCatalog;
import com.lorevault.api.cli.step.StepKey;
import com.lorevault.api.cli.step.StepOrchestrator;
import com.lorevault.api.ingestion.pipeline.StepResult;
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
        name = "step",
        description = "Pipeline step execution: run-step, list steps",
        subcommands = {
                StepCommand.RunStepCommand.class,
                StepCommand.StepsCommand.class
        }
)
@Slf4j
public class StepCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: step <run-step|steps>");
        return 0;
    }

    @Component
    @Command(name = "run-step", description = "Run a single pipeline step")
    static class RunStepCommand implements Callable<Integer> {

        @ParentCommand
        StepCommand parent;

        private final StepOrchestrator stepOrchestrator;

        RunStepCommand(StepOrchestrator stepOrchestrator) {
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
                System.out.printf("Step %s completed%n", stepKey);
                System.out.printf("  Success:  %s%n", result.success());
                System.out.printf("  Summary:  %s%n", result.summary());
                System.out.printf("  Duration: %dms%n", result.durationMs());
                if (!result.counts().isEmpty()) {
                    System.out.println("  Counts:");
                    result.counts().forEach((k, v) -> System.out.printf("    %s: %d%n", k, v));
                }
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
    @Command(name = "steps", description = "List available pipeline steps")
    static class StepsCommand implements Callable<Integer> {

        @ParentCommand
        StepCommand parent;

        private final StepCatalog stepCatalog;

        StepsCommand(StepCatalog stepCatalog) {
            this.stepCatalog = stepCatalog;
        }

        @Override
        public Integer call() {
            var steps = stepCatalog.all();
            System.out.printf("Available steps (%d registered):%n", steps.size());
            steps.forEach((key, def) -> {
                String prereqs = def.prerequisites().isEmpty()
                        ? "none"
                        : def.prerequisites().stream().map(StepKey::name).reduce((a, b) -> a + ", " + b).orElse("none");
                System.out.printf("  %-25s %s [prerequisites: %s]%n", key, def.description(), prereqs);
            });
            return 0;
        }
    }
}