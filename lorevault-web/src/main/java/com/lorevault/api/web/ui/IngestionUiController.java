package com.lorevault.api.web.ui;

import com.lorevault.api.content.domain.Universe;
import com.lorevault.api.content.domain.Series;
import com.lorevault.api.content.domain.Book;
import com.lorevault.api.library.LibraryResult;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.library.LibraryQueryService;
import com.lorevault.api.library.LibraryService;
import com.lorevault.api.web.command.ingestion.SubmitChapterRequest;
import com.lorevault.api.ingestion.application.result.IngestionSubmissionResult;
import com.lorevault.api.web.command.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.command.ingestion.extractor.FileContentExtractor;
import com.lorevault.api.web.command.ingestion.validation.FileUploadValidator;
import com.lorevault.api.web.ui.form.ChapterUploadForm;
import com.lorevault.api.web.ui.view.LibraryHierarchy;
import com.lorevault.api.web.ui.view.UniverseOption;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/ui/ingest")
@RequiredArgsConstructor
@Slf4j
public class IngestionUiController {

    private static final String NEW_SELECTION = "__new__";

    private final IngestionService ingestionService;
    private final FileUploadValidator fileUploadValidator;
    private final CoordinatesBuilder coordinatesBuilder;
    private final FileContentExtractor fileContentExtractor;
    private final LibraryQueryService libraryQueryService;
    private final LibraryService libraryService;

    @PostMapping("/chapter")
    public String uploadChapter(@Valid @ModelAttribute("chapterUploadForm") ChapterUploadForm form,
                                BindingResult bindingResult,
                                Model model) {
        populateModel(model);
        model.addAttribute("chapterUploadForm", form);

        UUID bookId = resolveBookSelection(form, bindingResult);

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
            return "ui/ingestion :: uploadForm";
        }

        var numberValidation = coordinatesBuilder.validateChapterNumber(form.getChapterNumber());
        if (!numberValidation.isValid()) {
            bindingResult.rejectValue("chapterNumber", "chapter.number.invalid", numberValidation.getErrorMessage());
            return "ui/ingestion :: uploadForm";
        }

        var contentResult = fileContentExtractor.extractFileContent(form.getFile());
        if (!contentResult.isSuccess()) {
            bindingResult.rejectValue("file", "chapter.file.read", contentResult.getErrorMessage());
            return "ui/ingestion :: uploadForm";
        }

        String finalChapterTitle = coordinatesBuilder.determineFinalTitle(form.getChapterTitle(), form.getFile().getOriginalFilename());
        var titleValidation = coordinatesBuilder.validateTitleLength(finalChapterTitle);
        if (!titleValidation.isValid()) {
            bindingResult.rejectValue("chapterTitle", "chapter.title.invalid", titleValidation.getErrorMessage());
            return "ui/ingestion :: uploadForm";
        }

        try {
            SubmitChapterRequest request = coordinatesBuilder.buildSubmitRequest(
                    bookId,
                    form.getChapterNumber(),
                    finalChapterTitle,
                    contentResult.getContent());

            IngestionSubmissionResult response = ingestionService.submitChapter(
                    request.getBookId(),
                    request.getChapterNumber(),
                    request.getChapterTitle(),
                    request.getChapterText()
            );
            log.info("[UI] Chapter submission queued. JobId={}, ChapterId={}",
                    response.jobId(), response.chapterId());

            ChapterUploadForm resetForm = new ChapterUploadForm();
            resetForm.setUniverseSelection(form.getUniverseSelection());
            resetForm.setSeriesSelection(form.getSeriesSelection());
            resetForm.setBookSelection(form.getBookSelection());
            model.addAttribute("chapterUploadForm", resetForm);
            populateModel(model);
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

    @PostMapping("/chapters")
    @ResponseBody
    public ResponseEntity<BatchUploadResponse> uploadChapters(@RequestParam String universeSelection,
                                                              @RequestParam(required = false) String seriesSelection,
                                                              @RequestParam String bookSelection,
                                                              @RequestParam(required = false) String newUniverseName,
                                                              @RequestParam(required = false) String newSeriesName,
                                                              @RequestParam(required = false) String newBookTitle,
                                                              @RequestParam(required = false) Integer newBookNumber,
                                                              @RequestParam("chapterNumbers") List<Integer> chapterNumbers,
                                                              @RequestParam(value = "chapterTitles", required = false) List<String> chapterTitles,
                                                              @RequestParam("clientIds") List<String> clientIds,
                                                              @RequestParam("files") List<MultipartFile> files) {
        ChapterUploadForm destinationForm = new ChapterUploadForm();
        destinationForm.setUniverseSelection(universeSelection);
        destinationForm.setSeriesSelection(seriesSelection);
        destinationForm.setBookSelection(bookSelection);
        destinationForm.setNewUniverseName(newUniverseName);
        destinationForm.setNewSeriesName(newSeriesName);
        destinationForm.setNewBookTitle(newBookTitle);
        destinationForm.setNewBookNumber(newBookNumber);

        BindingResult bindingResult = new BeanPropertyBindingResult(destinationForm, "chapterUploadForm");
        UUID resolvedBookId = resolveBookSelection(destinationForm, bindingResult);

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(BatchUploadResponse.failure(
                    "Add at least one chapter file before submitting.",
                    destinationForm.getUniverseSelection(),
                    destinationForm.getSeriesSelection(),
                    destinationForm.getBookSelection(),
                    List.of()
            ));
        }

        if (clientIds.size() != files.size() || chapterNumbers.size() != files.size()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BatchUploadResponse.failure(
                    "Upload payload was incomplete. Refresh the page and try again.",
                    destinationForm.getUniverseSelection(),
                    destinationForm.getSeriesSelection(),
                    destinationForm.getBookSelection(),
                    List.of()
            ));
        }

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Destination selection is invalid")
                    .findFirst()
                    .orElse("Destination selection is invalid");
            return ResponseEntity.badRequest().body(BatchUploadResponse.failure(
                    message,
                    destinationForm.getUniverseSelection(),
                    destinationForm.getSeriesSelection(),
                    destinationForm.getBookSelection(),
                    List.of()
            ));
        }

        List<BatchUploadItemResult> results = new ArrayList<>();
        int successCount = 0;

        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            Integer chapterNumber = chapterNumbers.get(index);
            String chapterTitle = getIndexedValue(chapterTitles, index);
            String clientId = clientIds.get(index);
            String filename = file != null && file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";

            if (chapterNumber == null || chapterNumber < 0) {
                results.add(BatchUploadItemResult.failed(clientId, filename, null, "Missing chapter number prefix before underscore."));
                continue;
            }

            var fileValidation = fileUploadValidator.validateFile(file);
            if (!fileValidation.isValid()) {
                results.add(BatchUploadItemResult.failed(clientId, filename, chapterNumber, fileValidation.getErrorMessage()));
                continue;
            }

            var contentResult = fileContentExtractor.extractFileContent(file);
            if (!contentResult.isSuccess()) {
                results.add(BatchUploadItemResult.failed(clientId, filename, chapterNumber, contentResult.getErrorMessage()));
                continue;
            }

            String finalChapterTitle = coordinatesBuilder.determineFinalTitle(chapterTitle, filename);
            var titleValidation = coordinatesBuilder.validateTitleLength(finalChapterTitle);
            if (!titleValidation.isValid()) {
                results.add(BatchUploadItemResult.failed(clientId, filename, chapterNumber, titleValidation.getErrorMessage()));
                continue;
            }

            try {
                SubmitChapterRequest request = coordinatesBuilder.buildSubmitRequest(
                        resolvedBookId,
                        chapterNumber,
                        finalChapterTitle,
                        contentResult.getContent());

                IngestionSubmissionResult response = ingestionService.submitChapter(
                        request.getBookId(),
                        request.getChapterNumber(),
                        request.getChapterTitle(),
                        request.getChapterText()
                );
                successCount++;
                results.add(BatchUploadItemResult.submitted(
                        clientId,
                        filename,
                        chapterNumber,
                        response.jobId(),
                        response.chapterId(),
                        "Queued for processing"
                ));
            } catch (Exception ex) {
                log.error("[UI] Failed to queue chapter from batch upload: {}", filename, ex);
                results.add(BatchUploadItemResult.failed(clientId, filename, chapterNumber, "Unexpected error while submitting chapter"));
            }
        }

        int failureCount = results.size() - successCount;
        String message = successCount > 0
                ? String.format("Queued %d chapter%s%s",
                successCount,
                successCount == 1 ? "" : "s",
                failureCount > 0 ? String.format(" (%d failed)", failureCount) : "")
                : "No chapters were queued. Fix the staged files and try again.";

        return ResponseEntity.ok(BatchUploadResponse.success(
                message,
                destinationForm.getUniverseSelection(),
                destinationForm.getSeriesSelection(),
                destinationForm.getBookSelection(),
                successCount,
                failureCount,
                results
        ));
    }

    private UUID resolveBookSelection(ChapterUploadForm form, BindingResult bindingResult) {
        String universeSelection = normalizeSelection(form.getUniverseSelection());
        String seriesSelection = normalizeSelection(form.getSeriesSelection());
        String bookSelection = normalizeSelection(form.getBookSelection());

        UUID universeId = resolveUniverse(form, universeSelection, bindingResult);
        if (bindingResult.hasErrors()) {
            return null;
        }

        UUID seriesId = resolveSeries(form, universeId, seriesSelection, bindingResult);
        if (bindingResult.hasErrors()) {
            return null;
        }

        return resolveBook(form, universeId, seriesId, bookSelection, bindingResult);
    }

    private UUID resolveUniverse(ChapterUploadForm form, String universeSelection, BindingResult bindingResult) {
        if (NEW_SELECTION.equals(universeSelection)) {
            if (isBlank(form.getNewUniverseName())) {
                bindingResult.rejectValue("newUniverseName", "universe.name.required", "Universe name is required when creating a new universe");
                return null;
            }
            LibraryResult<Universe> result = libraryService.createUniverse(form.getNewUniverseName().trim());
            form.setUniverseSelection(result.entity().getId().toString());
            return result.entity().getId();
        }

        UUID universeId = parseUuid(universeSelection);
        if (universeId == null) {
            bindingResult.rejectValue("universeSelection", "universe.selection.required", "Select an existing universe or create a new one");
        }
        return universeId;
    }

    private UUID resolveSeries(ChapterUploadForm form, UUID universeId, String seriesSelection, BindingResult bindingResult) {
        if (NEW_SELECTION.equals(seriesSelection)) {
            if (isBlank(form.getNewSeriesName())) {
                bindingResult.rejectValue("newSeriesName", "series.name.required", "Series name is required when creating a new series");
                return null;
            }
            LibraryResult<Series> result = libraryService.createSeries(universeId, form.getNewSeriesName().trim());
            form.setSeriesSelection(result.entity().getId().toString());
            return result.entity().getId();
        }

        return parseUuid(seriesSelection);
    }

    private UUID resolveBook(ChapterUploadForm form, UUID universeId, UUID seriesId, String bookSelection, BindingResult bindingResult) {
        if (NEW_SELECTION.equals(bookSelection)) {
            if (isBlank(form.getNewBookTitle())) {
                bindingResult.rejectValue("newBookTitle", "book.title.required", "Book title is required when creating a new book");
                return null;
            }
            if (seriesId != null && form.getNewBookNumber() == null) {
                bindingResult.rejectValue("newBookNumber", "book.number.required", "Book number is required when creating a book inside a series");
                return null;
            }
            LibraryResult<Book> result = libraryService.createBook(
                    universeId,
                    seriesId,
                    form.getNewBookTitle().trim(),
                    seriesId == null ? null : form.getNewBookNumber()
            );
            form.setBookSelection(result.entity().getId().toString());
            return result.entity().getId();
        }

        UUID bookId = parseUuid(bookSelection);
        if (bookId == null) {
            bindingResult.rejectValue("bookSelection", "book.selection.required", "Select an existing book or create a new one");
        }
        return bookId;
    }

    private void populateModel(Model model) {
        List<UniverseOption> universes = libraryQueryService.listUniverses().stream()
                .map(UniverseOption::from)
                .toList();
        model.addAttribute("universes", universes);

        List<LibraryQueryService.BookSummary> allBooks = universes.stream()
                .flatMap(u -> libraryQueryService.listBooksForUniverse(u.id()).stream())
                .toList();
        LibraryHierarchy hierarchy = LibraryHierarchy.from(allBooks);
        model.addAttribute("libraryHierarchy", hierarchy);
    }

    private UUID parseUuid(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeSelection(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String getIndexedValue(List<String> values, int index) {
        if (values == null || index >= values.size()) {
            return null;
        }
        String value = values.get(index);
        return value != null && !value.isBlank() ? value : null;
    }

    public record BatchUploadResponse(boolean success,
                                      String message,
                                      String universeSelection,
                                      String seriesSelection,
                                      String bookSelection,
                                      int successCount,
                                      int failureCount,
                                      List<BatchUploadItemResult> results) {

        public static BatchUploadResponse success(String message,
                                                  String universeSelection,
                                                  String seriesSelection,
                                                  String bookSelection,
                                                  int successCount,
                                                  int failureCount,
                                                  List<BatchUploadItemResult> results) {
            return new BatchUploadResponse(true, message, universeSelection, seriesSelection, bookSelection, successCount, failureCount, results);
        }

        public static BatchUploadResponse failure(String message,
                                                  String universeSelection,
                                                  String seriesSelection,
                                                  String bookSelection,
                                                  List<BatchUploadItemResult> results) {
            return new BatchUploadResponse(false, message, universeSelection, seriesSelection, bookSelection, 0, 0, results);
        }
    }

    public record BatchUploadItemResult(String clientId,
                                        String fileName,
                                        Integer chapterNumber,
                                        String status,
                                        String message,
                                        UUID jobId,
                                        UUID chapterId) {

        public static BatchUploadItemResult submitted(String clientId,
                                                      String fileName,
                                                      Integer chapterNumber,
                                                      UUID jobId,
                                                      UUID chapterId,
                                                      String message) {
            return new BatchUploadItemResult(clientId, fileName, chapterNumber, "submitted", message, jobId, chapterId);
        }

        public static BatchUploadItemResult failed(String clientId,
                                                   String fileName,
                                                   Integer chapterNumber,
                                                   String message) {
            return new BatchUploadItemResult(clientId, fileName, chapterNumber, "failed", message, null, null);
        }
    }
}
