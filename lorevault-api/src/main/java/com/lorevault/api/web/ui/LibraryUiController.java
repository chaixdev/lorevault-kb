package com.lorevault.api.web.ui;

import com.lorevault.api.dto.library.CreateBookRequest;
import com.lorevault.api.dto.library.CreateBookResponse;
import com.lorevault.api.dto.library.CreateSeriesRequest;
import com.lorevault.api.dto.library.CreateSeriesResponse;
import com.lorevault.api.dto.library.CreateUniverseRequest;
import com.lorevault.api.dto.library.CreateUniverseResponse;
import com.lorevault.api.service.library.LibraryQueryService;
import com.lorevault.api.service.library.LibraryService;
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
            CreateUniverseResponse response = libraryService.createUniverse(new CreateUniverseRequest(form.getName()));
            model.addAttribute("message", response.isCreated()
                    ? "Universe created successfully"
                    : "Universe already exists; loaded existing record");
            model.addAttribute("messageType", response.isCreated() ? "success" : "info");
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
            CreateSeriesResponse response = libraryService.createSeries(new CreateSeriesRequest(
                    form.getUniverseId(), form.getName().trim()));
            model.addAttribute("message", response.isCreated()
                    ? "Series created successfully"
                    : "Series already exists; loaded existing record");
            model.addAttribute("messageType", response.isCreated() ? "success" : "info");
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
            CreateBookRequest request = new CreateBookRequest(
                    form.getUniverseId(),
                    form.getSeriesId(),
                    form.getTitle().trim(),
                    form.getSeriesId() == null ? null : form.getBookNumber());
            CreateBookResponse response = libraryService.createBook(request);
            model.addAttribute("message", response.isCreated()
                    ? "Book created successfully"
                    : "Book already exists; loaded existing record");
            model.addAttribute("messageType", response.isCreated() ? "success" : "info");
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
                CreateUniverseResponse universeResponse = libraryService.createUniverse(
                        new CreateUniverseRequest(form.getNewUniverseName()));
                universeId = universeResponse.getUniverseId();
                universeName = universeResponse.getName();
                universeCreated = universeResponse.isCreated();
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
                CreateSeriesResponse seriesResponse = libraryService.createSeries(
                        new CreateSeriesRequest(universeId, form.getNewSeriesName()));
                seriesId = seriesResponse.getSeriesId();
                seriesName = seriesResponse.getName();
                seriesCreated = seriesResponse.isCreated();
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
            CreateBookRequest bookRequest = new CreateBookRequest(
                    universeId,
                    seriesId,
                    form.getBookTitle(),
                    seriesId == null ? null : form.getBookNumber());
            CreateBookResponse bookResponse = libraryService.createBook(bookRequest);

            // Build success message
            StringBuilder message = new StringBuilder();
            if (universeCreated) {
                message.append("Created universe '").append(universeName).append("'. ");
            }
            if (seriesCreated) {
                message.append("Created series '").append(seriesName).append("'. ");
            }
            if (bookResponse.isCreated()) {
                message.append("Created book '").append(form.getBookTitle()).append("'.");
            } else {
                message.append("Book '").append(form.getBookTitle()).append("' already exists.");
            }

            model.addAttribute("message", message.toString());
            model.addAttribute("messageType", bookResponse.isCreated() ? "success" : "info");
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
