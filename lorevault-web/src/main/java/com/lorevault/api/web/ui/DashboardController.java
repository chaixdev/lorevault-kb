package com.lorevault.api.web.ui;

import com.lorevault.api.library.LibraryQueryService;
import com.lorevault.api.web.ui.form.ChapterUploadForm;
import com.lorevault.api.web.ui.form.CreateBookForm;
import com.lorevault.api.web.ui.form.CreateLibraryForm;
import com.lorevault.api.web.ui.form.CreateSeriesForm;
import com.lorevault.api.web.ui.form.CreateUniverseForm;
import com.lorevault.api.web.ui.view.LibraryHierarchy;
import com.lorevault.api.web.ui.view.UniverseOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class DashboardController {

    private final LibraryQueryService libraryQueryService;

    @GetMapping
    public String dashboard(Model model) {
        List<UniverseOption> universes = libraryQueryService.listUniverses().stream()
            .map(UniverseOption::from)
            .toList();

        // Load all books for the hierarchical selector
        List<LibraryQueryService.BookSummary> allBooks = universes.stream()
                .flatMap(u -> libraryQueryService.listBooksForUniverse(u.id()).stream())
                .toList();
        LibraryHierarchy hierarchy = LibraryHierarchy.from(allBooks);

        model.addAttribute("universes", universes);
        model.addAttribute("libraryHierarchy", hierarchy);
        model.addAttribute("createUniverseForm", new CreateUniverseForm());
        model.addAttribute("createSeriesForm", new CreateSeriesForm());
        model.addAttribute("createBookForm", new CreateBookForm());
        model.addAttribute("createLibraryForm", new CreateLibraryForm());
        model.addAttribute("chapterUploadForm", new ChapterUploadForm());
        model.addAttribute("seriesForUniverse", java.util.List.of());
        model.addAttribute("booksForUniverse", java.util.List.of());
        model.addAttribute("books", java.util.List.of());

        return "ui/dashboard";
    }
}
