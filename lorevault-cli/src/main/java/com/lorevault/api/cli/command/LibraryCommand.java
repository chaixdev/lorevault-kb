package com.lorevault.api.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lorevault.api.library.book.Book;
import com.lorevault.api.library.series.Series;
import com.lorevault.api.library.service.LibraryResult;
import com.lorevault.api.library.service.LibraryService;
import com.lorevault.api.library.universe.Universe;
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
        name = "library",
        description = "Manage library structure: universes, series, books",
        subcommands = {LibraryCommand.CreateCommand.class}
)
@Slf4j
public class LibraryCommand implements Callable<Integer> {

    private final ObjectMapper objectMapper;

    public LibraryCommand(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() {
        System.out.println("Usage: library <create>");
        return 0;
    }

    @Component
    @Command(name = "create", description = "Create universe, series, and book in one step")
    static class CreateCommand implements Callable<Integer> {

        @ParentCommand
        LibraryCommand parent;

        private final LibraryService libraryService;

        CreateCommand(LibraryService libraryService) {
            this.libraryService = libraryService;
        }

        @Option(names = {"-u", "--universe"}, description = "Universe name", required = true)
        String universeName;

        @Option(names = {"-s", "--series"}, description = "Series name (omit for standalone book)")
        String seriesName;

        @Option(names = {"-b", "--book"}, description = "Book title", required = true)
        String bookTitle;

        @Option(names = {"-n", "--book-number"}, description = "Book number within series")
        Integer bookNumber;

        @Override
        public Integer call() {
            try {
                // 1. Create universe
                LibraryResult<Universe> universeResult = libraryService.createUniverse(universeName);
                UUID universeId = universeResult.entity().getId();

                // 2. Create series (if provided)
                UUID seriesId = null;
                String seriesLabel = null;
                LibraryResult<Series> seriesResult = null;
                if (seriesName != null && !seriesName.isBlank()) {
                    seriesResult = libraryService.createSeries(universeId, seriesName);
                    seriesId = seriesResult.entity().getId();
                    seriesLabel = seriesResult.entity().getName();
                }

                // 3. Create book
                LibraryResult<Book> bookResult = libraryService.createBook(universeId, seriesId, bookTitle, bookNumber);

                // 4. Output JSON
                ObjectNode output = parent.objectMapper.createObjectNode();
                output.put("universeId", universeId.toString())
                        .put("universeName", universeName)
                        .put("universeCreated", universeResult.isNew());

                if (seriesId != null) {
                    output.put("seriesId", seriesId.toString())
                            .put("seriesName", seriesLabel)
                            .put("seriesCreated", seriesResult.isNew());
                }

                output.put("bookId", bookResult.entity().getId().toString())
                        .put("bookTitle", bookTitle)
                        .put("bookCreated", bookResult.isNew());

                System.out.println(parent.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
                return 0;
            } catch (Exception e) {
                System.err.printf("Error: %s%n", e.getMessage());
                log.error("Library create failed", e);
                return 1;
            }
        }
    }
}