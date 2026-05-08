package com.lorevault.api.cli;

import com.lorevault.api.cli.command.JobsCommand;
import com.lorevault.api.cli.command.LibraryCommand;
import com.lorevault.api.cli.command.PrepareCommand;
import com.lorevault.api.cli.command.StatusCommand;
import com.lorevault.api.cli.command.StepCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Registers all CLI commands with Picocli and creates the {@link CommandLineRunner}
 * that executes the command passed as program arguments.
 */
@Configuration
public class CliConfig {

    @Bean
    public CommandLine commandLine(IFactory factory,
                                   LibraryCommand libraryCommand,
                                   PrepareCommand prepareCommand,
                                   StatusCommand statusCommand,
                                   JobsCommand jobsCommand,
                                   StepCommand stepCommand) {
        CommandLine cli = new CommandLine(LoreVaultCliApplication.class, factory);
        cli.addSubcommand("library", libraryCommand);
        cli.addSubcommand("prepare", prepareCommand);
        cli.addSubcommand("status", statusCommand);
        cli.addSubcommand("jobs", jobsCommand);
        cli.addSubcommand("step", stepCommand);
        return cli;
    }

    @Bean
    public CommandLineRunner commandLineRunner(CommandLine cli) {
        return args -> {
            int exitCode = cli.execute(args);
            System.exit(exitCode);
        };
    }
}