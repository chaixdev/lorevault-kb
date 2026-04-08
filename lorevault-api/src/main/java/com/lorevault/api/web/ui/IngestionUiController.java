package com.lorevault.api.web.ui;

import com.lorevault.api.support.SubmitChapterRequest;
import com.lorevault.api.support.SubmitChapterResponse;
import com.lorevault.api.ingestion.IngestionService;
import com.lorevault.api.library.LibraryQueryService;
import com.lorevault.api.web.command.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.command.ingestion.extractor.FileContentExtractor;
import com.lorevault.api.web.command.ingestion.validation.FileUploadValidator;
import com.lorevault.api.web.ui.form.ChapterUploadForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui/ingest")
@RequiredArgsConstructor
@Slf4j
public class IngestionUiController {

    private final IngestionService ingestionService;
    private final FileUploadValidator fileUploadValidator;
    private final CoordinatesBuilder coordinatesBuilder;
    private final FileContentExtractor fileContentExtractor;
    private final LibraryQueryService libraryQueryService;

    @PostMapping("/chapter")
    public String uploadChapter(@Valid @ModelAttribute("chapterUploadForm") ChapterUploadForm form,
                                BindingResult bindingResult,
                                Model model) {
        populateModel(model, form.getUniverseId());
        model.addAttribute("chapterUploadForm", form);

        if (form.getFile() != null && form.getFile().isEmpty()) {
            bindingResult.rejectValue("file", "chapter.file.empty", "Uploaded file is empty");
            return "ui/ingestion :: uploadForm";
        }

        if (bindingResult.hasErrors()) {
            return "ui/ingestion :: uploadForm";
        }

        var fileValidation = fileUploadValidator.validateFile(form.getFile());
        if (!fileValidation.isValid()) {
            bindingResult.rejectValue("file", "chapter.file.invalid", fileValidation.getErrorMessage());
            model.addAttribute("chapterUploadForm", form);
            return "ui/ingestion :: uploadForm";
        }

        var numberValidation = coordinatesBuilder.validateChapterNumber(form.getChapterNumber());
        if (!numberValidation.isValid()) {
            bindingResult.rejectValue("chapterNumber", "chapter.number.invalid", numberValidation.getErrorMessage());
            model.addAttribute("chapterUploadForm", form);
            return "ui/ingestion :: uploadForm";
        }

        var contentResult = fileContentExtractor.extractFileContent(form.getFile());
        if (!contentResult.isSuccess()) {
            bindingResult.rejectValue("file", "chapter.file.read", contentResult.getErrorMessage());
            model.addAttribute("chapterUploadForm", form);
            return "ui/ingestion :: uploadForm";
        }

        String finalChapterTitle = coordinatesBuilder.determineFinalTitle(form.getChapterTitle(), form.getFile().getOriginalFilename());
        var titleValidation = coordinatesBuilder.validateTitleLength(finalChapterTitle);
        if (!titleValidation.isValid()) {
            bindingResult.rejectValue("chapterTitle", "chapter.title.invalid", titleValidation.getErrorMessage());
            model.addAttribute("chapterUploadForm", form);
            return "ui/ingestion :: uploadForm";
        }

        try {
            SubmitChapterRequest request = coordinatesBuilder.buildSubmitRequest(
                    form.getBookId(),
                    form.getChapterNumber(),
                    finalChapterTitle,
                    contentResult.getContent());

            SubmitChapterResponse response = ingestionService.submitChapter(request);
            log.info("[UI] Chapter submission queued. JobId={}, ChapterId={}",
                    response.getJobId(), response.getChapterId());

            ChapterUploadForm resetForm = new ChapterUploadForm();
            resetForm.setUniverseId(form.getUniverseId());
            model.addAttribute("chapterUploadForm", resetForm);
            populateModel(model, resetForm.getUniverseId());
            model.addAttribute("message", "Chapter upload request submitted successfully");
            model.addAttribute("messageType", "success");
            model.addAttribute("triggerJobRefresh", true);
        } catch (Exception ex) {
            log.error("[UI] Unexpected error during chapter upload", ex);
            bindingResult.reject("chapter.upload.failed", "Unexpected error while submitting chapter for ingestion");
            model.addAttribute("chapterUploadForm", form);
        }

        return "ui/ingestion :: uploadForm";
    }

    private void populateModel(Model model, java.util.UUID universeId) {
    java.util.List<com.lorevault.api.web.ui.view.UniverseOption> universes = libraryQueryService.listUniverses().stream()
        .map(com.lorevault.api.web.ui.view.UniverseOption::from)
        .toList();
    model.addAttribute("universes", universes);

    // Load all books for the hierarchical selector
    java.util.List<LibraryQueryService.BookSummary> allBooks = universes.stream()
            .flatMap(u -> libraryQueryService.listBooksForUniverse(u.id()).stream())
            .toList();
    com.lorevault.api.web.ui.view.LibraryHierarchy hierarchy = com.lorevault.api.web.ui.view.LibraryHierarchy.from(allBooks);
    model.addAttribute("libraryHierarchy", hierarchy);

    java.util.List<com.lorevault.api.web.ui.view.BookOption> books = universeId != null
        ? libraryQueryService.listBooksForUniverse(universeId).stream()
        .map(com.lorevault.api.web.ui.view.BookOption::from)
        .toList()
        : java.util.List.of();
    model.addAttribute("books", books);
    model.addAttribute("booksForUniverse", books);
    }
}
