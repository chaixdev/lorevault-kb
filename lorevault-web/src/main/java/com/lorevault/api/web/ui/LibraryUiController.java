package com.lorevault.api.web.ui;

import com.lorevault.api.content.domain.Universe;
import com.lorevault.api.content.domain.Series;
import com.lorevault.api.content.domain.Book;
import com.lorevault.api.library.LibraryResult;
import com.lorevault.api.library.LibraryQueryService;
import com.lorevault.api.library.LibraryService;
import com.lorevault.api.web.ui.form.CreateBookForm;
import com.lorevault.api.web.ui.form.CreateLibraryForm;
import com.lorevault.api.web.ui.form.CreateSeriesForm;
import com.lorevault.api.web.ui.form.CreateUniverseForm;
import com.lorevault.api.web.ui.view.SeriesOption;
import com.lorevault.api.web.ui.view.UniverseOption;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/ui/library")
@RequiredArgsConstructor
@Slf4j
public class LibraryUiController {

    private final LibraryService libraryService;
    private final LibraryQueryService libraryQueryService;

    @PostMapping("/universes")
    public String createUniverse(@Valid @ModelAttribute("createUniverseForm") CreateUniverseForm form,
                                 BindingResult bindingResult,
                                 Model model) {
        if (form.getName() != null) {
            form.setName(form.getName().trim());
        }

        if (bindingResult.hasErrors()) {
            populateCommonModel(model);
            model.addAttribute("createUniverseForm", form);
            model.addAttribute("form", form);
            return "ui/library :: universeForm";
        }

        try {
            LibraryResult<Universe> result = libraryService.createUniverse(form.getName());
            model.addAttribute("message", result.isNew()
                    ? "Universe created successfully"
                    : "Universe already exists; loaded existing record");
            model.addAttribute("messageType", result.isNew() ? "success" : "info");
            model.addAttribute("refreshUniverses", true);
            CreateUniverseForm resetForm = new CreateUniverseForm();
            model.addAttribute("createUniverseForm", resetForm);
            model.addAttribute("form", resetForm);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("universe.invalid", ex.getMessage());
            model.addAttribute("createUniverseForm", form);
            model.addAttribute("form", form);
        }

        populateCommonModel(model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", model.getAttribute("createUniverseForm"));
        }
        return "ui/library :: universeForm";
    }

    @PostMapping("/series")
    public String createSeries(@Valid @ModelAttribute("createSeriesForm") CreateSeriesForm form,
                               BindingResult bindingResult,
                               Model model) {
        if (form.getName() != null) {
            form.setName(form.getName().trim());
        }
        populateCommonModel(model);
        model.addAttribute("createSeriesForm", form);
        model.addAttribute("form", form);

        if (bindingResult.hasErrors()) {
            return "ui/library :: seriesForm";
        }

        try {
            LibraryResult<Series> result = libraryService.createSeries(
                    form.getUniverseId(), form.getName().trim());
            model.addAttribute("message", result.isNew()
                    ? "Series created successfully"
                    : "Series already exists; loaded existing record");
            model.addAttribute("messageType", result.isNew() ? "success" : "info");
            model.addAttribute("refreshSeriesUniverseId", form.getUniverseId());
            CreateSeriesForm resetForm = new CreateSeriesForm();
            resetForm.setUniverseId(form.getUniverseId());
            model.addAttribute("createSeriesForm", resetForm);
            model.addAttribute("form", resetForm);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("series.invalid", ex.getMessage());
            model.addAttribute("form", form);
        }

        return "ui/library :: seriesForm";
    }

    @PostMapping("/books")
    public String createBook(@Valid @ModelAttribute("createBookForm") CreateBookForm form,
                             BindingResult bindingResult,
                             Model model) {
        if (form.getTitle() != null) {
            form.setTitle(form.getTitle().trim());
        }
        populateCommonModel(model);
        model.addAttribute("createBookForm", form);
        List<SeriesOption> seriesForUniverse = listSeriesSafe(form.getUniverseId());
        model.addAttribute("seriesForUniverse", seriesForUniverse);
        model.addAttribute("seriesList", seriesForUniverse);
        model.addAttribute("form", form);

        if (form.getSeriesId() != null && form.getBookNumber() == null) {
            bindingResult.rejectValue("bookNumber", "book.bookNumber.required",
                    "Book number is required when creating a book within a series");
        }

        if (bindingResult.hasErrors()) {
            return "ui/library :: bookForm";
        }

        try {
            LibraryResult<Book> result = libraryService.createBook(
                    form.getUniverseId(),
                    form.getSeriesId(),
                    form.getTitle().trim(),
                    form.getSeriesId() == null ? null : form.getBookNumber());
            model.addAttribute("message", result.isNew()
                    ? "Book created successfully"
                    : "Book already exists; loaded existing record");
            model.addAttribute("messageType", result.isNew() ? "success" : "info");
            model.addAttribute("refreshBooksUniverseId", form.getUniverseId());
            CreateBookForm resetForm = new CreateBookForm();
            resetForm.setUniverseId(form.getUniverseId());
            model.addAttribute("createBookForm", resetForm);
            List<SeriesOption> refreshedSeries = listSeriesSafe(form.getUniverseId());
            model.addAttribute("seriesForUniverse", refreshedSeries);
            model.addAttribute("seriesList", refreshedSeries);
            model.addAttribute("form", resetForm);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("book.invalid", ex.getMessage());
            model.addAttribute("form", form);
        }

        if (!model.containsAttribute("seriesList")) {
            model.addAttribute("seriesList", model.getAttribute("seriesForUniverse"));
        }
        return "ui/library :: bookForm";
    }

    private void populateCommonModel(Model model) {
        model.addAttribute("universes", libraryQueryService.listUniverses().stream()
                .map(UniverseOption::from)
                .toList());
    }

    private List<SeriesOption> listSeriesSafe(UUID universeId) {
        return universeId == null ? List.of() : libraryQueryService.listSeries(universeId).stream()
                .map(SeriesOption::from)
                .toList();
    }

    @PostMapping("/combined")
    public String createCombined(@Valid @ModelAttribute("createLibraryForm") CreateLibraryForm form,
                                 BindingResult bindingResult,
                                 Model model) {
        // Trim all string inputs
        if (form.getNewUniverseName() != null) {
            form.setNewUniverseName(form.getNewUniverseName().trim());
        }
        if (form.getNewSeriesName() != null) {
            form.setNewSeriesName(form.getNewSeriesName().trim());
        }
        if (form.getBookTitle() != null) {
            form.setBookTitle(form.getBookTitle().trim());
        }

        // Custom validation: Must have either existing universe or new universe name
        if (!form.hasValidUniverse()) {
            bindingResult.rejectValue("newUniverseName", "universe.required",
                    "Either select an existing universe or provide a name for a new one");
        }

        // If creating new series, must have valid universe
        if (form.isCreatingNewSeries() && !form.hasValidUniverse()) {
            bindingResult.rejectValue("newSeriesName", "series.universeRequired",
                    "Cannot create series without a valid universe");
        }

        // If book number provided, must have series
        if (form.getBookNumber() != null && !form.isCreatingNewSeries() && form.getExistingSeriesId() == null) {
            bindingResult.rejectValue("bookNumber", "book.seriesRequired",
                    "Book number is only valid when the book belongs to a series");
        }

        populateCommonModel(model);
        model.addAttribute("createLibraryForm", form);
        
        if (bindingResult.hasErrors()) {
            return "ui/library :: combinedForm";
        }

        try {
            UUID universeId;
            String universeName;
            boolean universeCreated = false;
            
            // Step 1: Get or create universe
            if (form.isCreatingNewUniverse()) {
                LibraryResult<Universe> universeResult = libraryService.createUniverse(form.getNewUniverseName());
                universeId = universeResult.entity().getId();
                universeName = universeResult.entity().getName();
                universeCreated = universeResult.isNew();
            } else {
                universeId = form.getExistingUniverseId();
                universeName = libraryQueryService.listUniverses().stream()
                        .filter(u -> u.id().equals(universeId))
                        .findFirst()
                        .map(u -> u.name())
                        .orElse("Unknown");
            }

            // Step 2: Get or create series (if requested)
            UUID seriesId = null;
            String seriesName = null;
            boolean seriesCreated = false;
            
            if (form.isCreatingNewSeries()) {
                LibraryResult<Series> seriesResult = libraryService.createSeries(universeId, form.getNewSeriesName());
                seriesId = seriesResult.entity().getId();
                seriesName = seriesResult.entity().getName();
                seriesCreated = seriesResult.isNew();
            } else if (form.getExistingSeriesId() != null) {
                UUID existingSeriesId = form.getExistingSeriesId();
                seriesId = existingSeriesId;
                seriesName = libraryQueryService.listSeries(universeId).stream()
                        .filter(s -> s.id().equals(existingSeriesId))
                        .findFirst()
                        .map(s -> s.name())
                        .orElse("Unknown");
            }

            // Step 3: Create book
            LibraryResult<Book> bookResult = libraryService.createBook(
                    universeId,
                    seriesId,
                    form.getBookTitle(),
                    seriesId == null ? null : form.getBookNumber());

            // Build success message
            StringBuilder message = new StringBuilder();
            if (universeCreated) {
                message.append("Created universe '").append(universeName).append("'. ");
            }
            if (seriesCreated) {
                message.append("Created series '").append(seriesName).append("'. ");
            }
            if (bookResult.isNew()) {
                message.append("Created book '").append(form.getBookTitle()).append("'.");
            } else {
                message.append("Book '").append(form.getBookTitle()).append("' already exists.");
            }

            model.addAttribute("message", message.toString());
            model.addAttribute("messageType", bookResult.isNew() ? "success" : "info");
            model.addAttribute("refreshUniverses", true);
            
            // Reset form
            CreateLibraryForm resetForm = new CreateLibraryForm();
            model.addAttribute("createLibraryForm", resetForm);
            
        } catch (IllegalArgumentException ex) {
            log.error("Error creating library entities", ex);
            bindingResult.reject("library.invalid", ex.getMessage());
            model.addAttribute("createLibraryForm", form);
        }

        populateCommonModel(model);
        return "ui/library :: combinedForm";
    }
}
