package com.lorevault.api.web.ui;

import com.lorevault.api.service.library.LibraryQueryService;
import com.lorevault.api.web.ui.view.BookOption;
import com.lorevault.api.web.ui.view.SeriesOption;
import com.lorevault.api.web.ui.view.UniverseOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/ui/library/options")
@RequiredArgsConstructor
@Slf4j
public class LibraryOptionsController {

    private final LibraryQueryService libraryQueryService;

    @GetMapping("/universes")
    public String universeOptions(@RequestParam(value = "selected", required = false) UUID selectedUniverseId,
                                  @RequestParam(value = "universeId", required = false) UUID universeId,
                                  Model model) {
        List<UniverseOption> universes = libraryQueryService.listUniverses().stream()
                .map(UniverseOption::from)
                .toList();
        UUID resolvedSelected = selectedUniverseId != null ? selectedUniverseId : universeId;
        model.addAttribute("universes", universes);
        model.addAttribute("selectedUniverseId", resolvedSelected);
        return "ui/options :: universeOptions";
    }

    @GetMapping("/series")
    public String seriesOptions(@RequestParam(value = "universeId", required = false) UUID universeId,
                                @RequestParam(value = "selected", required = false) UUID selectedSeriesId,
                                @RequestParam(value = "seriesId", required = false) UUID seriesId,
                                Model model) {
        log.info("[OPTIONS] Loading series options for universeId={}", universeId);
        List<SeriesOption> series = universeId == null ? List.of() : libraryQueryService.listSeries(universeId).stream()
                .map(SeriesOption::from)
                .toList();
        log.info("[OPTIONS] Found {} series for universeId={}", series.size(), universeId);
        UUID resolvedSelected = selectedSeriesId != null ? selectedSeriesId : seriesId;
        model.addAttribute("series", series);
        model.addAttribute("selectedSeriesId", resolvedSelected);
        return "ui/options :: seriesOptions";
    }

    @GetMapping("/books")
    public String bookOptions(@RequestParam(value = "universeId", required = false) UUID universeId,
                              @RequestParam(value = "seriesId", required = false) UUID seriesId,
                              @RequestParam(value = "selected", required = false) UUID selectedBookId,
                              @RequestParam(value = "bookId", required = false) UUID bookId,
                              Model model) {
        List<BookOption> books;
        if (seriesId != null) {
            books = libraryQueryService.listBooksForSeries(seriesId).stream()
                    .map(BookOption::from)
                    .toList();
        } else if (universeId != null) {
            books = libraryQueryService.listBooksForUniverse(universeId).stream()
                    .map(BookOption::from)
                    .toList();
        } else {
            books = List.of();
        }
        UUID resolvedSelected = selectedBookId != null ? selectedBookId : bookId;
        model.addAttribute("books", books);
        model.addAttribute("selectedBookId", resolvedSelected);
        return "ui/options :: bookOptions";
    }
}
