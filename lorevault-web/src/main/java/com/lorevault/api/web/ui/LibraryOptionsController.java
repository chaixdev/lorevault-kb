package com.lorevault.api.web.ui;

import com.lorevault.api.library.service.LibraryQueryService;
import com.lorevault.api.web.ui.view.BookOption;
import com.lorevault.api.web.ui.view.LibraryHierarchy;
import com.lorevault.api.web.ui.view.SeriesOption;
import com.lorevault.api.web.ui.view.UniverseOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/ui/library-options")
@RequiredArgsConstructor
public class LibraryOptionsController {

    private final LibraryQueryService libraryQueryService;

    @GetMapping("/universes")
    public String universeOptions(@RequestParam(value = "selected", required = false) String selectedUniverseId,
                                  @RequestParam(value = "universeId", required = false) String universeId,
                                  @RequestParam(value = "universeSelection", required = false) String universeSelection,
                                  @RequestParam(value = "includeCreateOption", defaultValue = "false") boolean includeCreateOption,
                                  Model model) {
        List<UniverseOption> universes = libraryQueryService.listUniverses().stream()
                .map(UniverseOption::from)
                .toList();
        String resolvedSelected = firstNonBlank(selectedUniverseId, universeSelection, universeId);
        model.addAttribute("universes", universes);
        model.addAttribute("selectedValue", resolvedSelected);
        model.addAttribute("includeCreateOption", includeCreateOption);
        return "ui/options :: universeOptions";
    }

    @GetMapping("/series")
    public String seriesOptions(@RequestParam(value = "universeId", required = false) String universeId,
                                @RequestParam(value = "universeSelection", required = false) String universeSelection,
                                @RequestParam(value = "selected", required = false) String selectedSeriesId,
                                @RequestParam(value = "seriesId", required = false) String seriesId,
                                @RequestParam(value = "seriesSelection", required = false) String seriesSelection,
                                @RequestParam(value = "includeCreateOption", defaultValue = "false") boolean includeCreateOption,
                                Model model) {
        String resolvedUniverseSelection = firstNonBlank(universeSelection, universeId);
        UUID resolvedUniverseId = parseUuid(resolvedUniverseSelection);
        log.info("[OPTIONS] Loading series options for universeId={}", resolvedUniverseId);
        List<SeriesOption> series = resolvedUniverseId == null ? List.of() : libraryQueryService.listSeries(resolvedUniverseId).stream()
                .map(SeriesOption::from)
                .toList();
        log.info("[OPTIONS] Found {} series for universeId={}", series.size(), resolvedUniverseId);
        String resolvedSelected = firstNonBlank(selectedSeriesId, seriesSelection, seriesId);
        if (resolvedSelected != null && !NEW_SELECTION.equals(resolvedSelected) && parseUuid(resolvedSelected) == null) {
            resolvedSelected = null;
        }
        model.addAttribute("series", series);
        model.addAttribute("selectedValue", resolvedSelected);
        model.addAttribute("includeCreateOption", includeCreateOption);
        return "ui/options :: seriesOptions";
    }

    @GetMapping("/books")
    public String bookOptions(@RequestParam(value = "universeId", required = false) String universeId,
                              @RequestParam(value = "universeSelection", required = false) String universeSelection,
                              @RequestParam(value = "seriesId", required = false) String seriesId,
                              @RequestParam(value = "seriesSelection", required = false) String seriesSelection,
                              @RequestParam(value = "selected", required = false) String selectedBookId,
                              @RequestParam(value = "bookId", required = false) String bookId,
                              @RequestParam(value = "bookSelection", required = false) String bookSelection,
                              @RequestParam(value = "includeCreateOption", defaultValue = "false") boolean includeCreateOption,
                              Model model) {
        List<BookOption> books;
        String resolvedUniverseSelection = firstNonBlank(universeSelection, universeId);
        String resolvedSeriesSelection = firstNonBlank(seriesSelection, seriesId);
        UUID resolvedSeriesId = parseUuid(resolvedSeriesSelection);
        UUID resolvedUniverseId = parseUuid(resolvedUniverseSelection);
        if (resolvedSeriesId != null) {
            books = libraryQueryService.listBooksForSeries(resolvedSeriesId).stream()
                    .map(BookOption::from)
                    .toList();
        } else if (resolvedUniverseId != null && (resolvedSeriesSelection == null || resolvedSeriesSelection.isBlank())) {
            books = libraryQueryService.listBooksForUniverse(resolvedUniverseId).stream()
                    .map(BookOption::from)
                    .toList();
        } else {
            books = List.of();
        }
        String resolvedSelected = firstNonBlank(selectedBookId, bookSelection, bookId);
        if (resolvedSelected != null && !NEW_SELECTION.equals(resolvedSelected) && parseUuid(resolvedSelected) == null) {
            resolvedSelected = null;
        }
        model.addAttribute("books", books);
        model.addAttribute("selectedValue", resolvedSelected);
        model.addAttribute("includeCreateOption", includeCreateOption);
        return "ui/options :: bookOptions";
    }

        @GetMapping("/book-selector")
    public String getBookSelector(Model model) {
        // TODO: Implement book hierarchy query
        LibraryHierarchy hierarchy = new LibraryHierarchy(List.of());
        model.addAttribute("libraryHierarchy", hierarchy);
        return "ui/ingestion :: bookSelectorContent";
    }

    @GetMapping("/book-chapters/{bookId}")
    public String getBookChapters(@PathVariable UUID bookId, Model model) {
        log.info("[CHAPTERS] Fetching chapters for bookId={}", bookId);
        
        List<ChapterSummary> chapters = libraryQueryService.listChaptersForBook(bookId).stream()
                .map(ch -> new ChapterSummary(
                        ch.id(),
                        ch.chapterNumber(),
                        ch.title(),
                        ch.sceneCount(),
                        null // Status not available yet
                ))
                .toList();
        
        log.info("[CHAPTERS] Found {} chapters for bookId={}: {}", 
                chapters.size(), bookId, 
                chapters.stream().map(ch -> String.format("Ch%d: %s", ch.chapterNumber(), ch.title())).toList());
        
        model.addAttribute("chapters", chapters);
        model.addAttribute("bookId", bookId);
        return "ui/ingestion :: bookChaptersContent";
    }

    public record ChapterSummary(
            UUID chapterId,
            Integer chapterNumber,
            String title,
            Integer sceneCount,
            String status
    ) {}

    private static final String NEW_SELECTION = "__new__";

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank() || NEW_SELECTION.equals(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
