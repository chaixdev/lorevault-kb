package com.lorevault.api.web.ingestion;

import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.service.ingestion.IngestionService;
import com.lorevault.api.web.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.ingestion.extractor.FileContentExtractor;
import com.lorevault.api.web.ingestion.response.ErrorResponseFactory;
import com.lorevault.api.web.ingestion.validation.FileUploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for content ingestion (Command operations)
 * Delegates validation, coordinate building, and file processing to focused services.
 */
@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@Slf4j
public class ContentIngestionController {

    private final IngestionService ingestionService;
    private final FileUploadValidator fileUploadValidator;
    private final CoordinatesBuilder coordinatesBuilder;
    private final ErrorResponseFactory errorResponseFactory;
    private final FileContentExtractor fileContentExtractor;

    /**
     * Submit a file for processing
     */
    @PostMapping(value = "/submit-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("universe") String universe,
            @RequestParam(value = "series", required = false) String series,
            @RequestParam("bookNumber") Integer bookNumber,
            @RequestParam("chapterNumber") Integer chapterNumber,
            @RequestParam(value = "partNumber", required = false) Integer partNumber,
            @RequestParam(value = "title", required = false) String title) {
        
        log.info("File upload request: universe={}, series={}, book={}, chapter={}, part={}, title={}, filename={}", 
                universe, series, bookNumber, chapterNumber, partNumber, title, file.getOriginalFilename());

        try {
            // Validate file is present
            if (file == null || file.isEmpty()) {
                return errorResponseFactory.createMissingFileError();
            }

            // Validate file upload (type, size, content)
            FileUploadValidator.ValidationResult fileValidation = fileUploadValidator.validateFile(file);
            if (!fileValidation.isValid()) {
                return errorResponseFactory.createFileValidationError(fileValidation);
            }

            // Validate coordinates parameters
            CoordinatesBuilder.CoordinateValidationResult coordinateValidation = 
                coordinatesBuilder.validateCoordinates(universe, series, bookNumber, chapterNumber, partNumber);
            if (!coordinateValidation.isValid()) {
                return errorResponseFactory.createCoordinateValidationError(coordinateValidation);
            }

            // Extract file content
            FileContentExtractor.ContentExtractionResult contentResult = fileContentExtractor.extractFileContent(file);
            if (!contentResult.isSuccess()) {
                return errorResponseFactory.createFileReadingError(contentResult.getFilename(), contentResult.getCause());
            }

            // Determine final chapter title
            String finalTitle = coordinatesBuilder.determineFinalTitle(title, file.getOriginalFilename());
            
            // Validate title length
            CoordinatesBuilder.CoordinateValidationResult titleValidation = coordinatesBuilder.validateTitleLength(finalTitle);
            if (!titleValidation.isValid()) {
                return errorResponseFactory.createCoordinateValidationError(titleValidation);
            }

            // Build coordinates and request
            PublicationCoordinates coordinates = coordinatesBuilder.buildCoordinates(universe, series, bookNumber, chapterNumber);
            SubmitChapterRequest request = coordinatesBuilder.buildSubmitRequest(coordinates, finalTitle, contentResult.getContent());

            // Submit for processing
            SubmitChapterResponse response = ingestionService.submitChapter(request);
            
            log.info("File submitted successfully: jobId={}, chapterId={}", 
                    response.getJobId(), response.getChapterId());

            return errorResponseFactory.createIngestionSuccessResponse(response.getJobId().toString());
            
        } catch (Exception e) {
            log.error("Unexpected error during file submission: {}", file.getOriginalFilename(), e);
            return errorResponseFactory.createIngestionServiceError(e);
        }
    }
}
