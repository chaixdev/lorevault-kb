package com.lorevault.api.cli;

import com.lorevault.api.cli.command.JobsCommand;
import com.lorevault.api.cli.command.LibraryCommand;
import com.lorevault.api.cli.command.PrepareCommand;
import com.lorevault.api.cli.command.StatusCommand;
import com.lorevault.api.cli.command.StepCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * CLI entry point for LoreVault.
 *
 * <p>Boots the Spring context without a web server (WebApplicationType.NONE),
 * providing command-line access to domain logic and step-by-step pipeline execution.
 *
 * <p>Command surface:
 * <pre>
 *   lorevault library create  -u "Universe" [-s "Series"] -b "Book Title" [-n bookNumber]
 *   lorevault prepare          -b &lt;bookUuid&gt; -n &lt;chapterNumber&gt; [-t "Title"] &lt;chapter-file|-&gt;
 *   lorevault step run         SCENE_DETECTION --job &lt;uuid&gt; --chapter &lt;uuid&gt;
 *   lorevault step list
 *   lorevault status           &lt;jobId&gt;
 *   lorevault jobs list        [--universe U] [--status S] [--limit N] [--offset O]
 * </pre>
 */
@SpringBootApplication(
        scanBasePackages = "com.lorevault.api",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan("com.lorevault.api")
@EnableNeo4jRepositories(basePackages = "com.lorevault.api")
@Command(name = "lorevault", mixinStandardHelpOptions = true,
        description = "LoreVault CLI for library management and pipeline execution")
public class LoreVaultCliApplication implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: lorevault <library|prepare|step|status|jobs>");
        return 0;
    }

    public static void main(String[] args) {
        SpringApplication.run(LoreVaultCliApplication.class, args);
    }
}