package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.support.SubmitChapterRequest;
import com.lorevault.api.support.SubmitChapterResponse;
import com.lorevault.api.ingestion.IngestionService;
import com.lorevault.api.web.command.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.command.ingestion.extractor.FileContentExtractor;
import com.lorevault.api.web.command.ingestion.response.ErrorResponseFactory;
import com.lorevault.api.web.command.ingestion.validation.FileUploadValidator;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * CQRS Command controller for content ingestion
 */
@RestController
@RequestMapping("/api/command/ingest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ingestion", description = "Content ingestion operations")
public class CommandIngestionController {

	private final IngestionService ingestionService;
	private final FileUploadValidator fileUploadValidator;
	private final CoordinatesBuilder coordinatesBuilder;
	private final ErrorResponseFactory errorResponseFactory;
	private final FileContentExtractor fileContentExtractor;

	/**
	 * Submit a file for processing
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> submitFile(
			@RequestParam("file") MultipartFile file,
			@RequestParam("bookId") java.util.UUID bookId,
			@RequestParam("chapterNumber") Integer chapterNumber,
			@RequestParam(value = "chapterTitle", required = false) String chapterTitle) {

		log.info("[CMD] Ingest: bookId={}, chapterNumber={}, chapterTitle={}, filename={}",
				bookId, chapterNumber, chapterTitle, file.getOriginalFilename());

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

			// Validate chapter number
			CoordinatesBuilder.CoordinateValidationResult numValidation = coordinatesBuilder.validateChapterNumber(chapterNumber);
			if (!numValidation.isValid()) {
				return errorResponseFactory.createCoordinateValidationError(numValidation);
			}

			// Extract file content
			FileContentExtractor.ContentExtractionResult contentResult = fileContentExtractor.extractFileContent(file);
			if (!contentResult.isSuccess()) {
				return errorResponseFactory.createFileReadingError(contentResult.getFilename(), contentResult.getCause());
			}

			// Determine final chapter title
			String finalChapterTitle = coordinatesBuilder.determineFinalTitle(chapterTitle, file.getOriginalFilename());
            
			// Validate title length
			CoordinatesBuilder.CoordinateValidationResult titleValidation = coordinatesBuilder.validateTitleLength(finalChapterTitle);
			if (!titleValidation.isValid()) {
				return errorResponseFactory.createCoordinateValidationError(titleValidation);
			}

			// Build request
			SubmitChapterRequest request = coordinatesBuilder.buildSubmitRequest(bookId, chapterNumber, finalChapterTitle, contentResult.getContent());

			// Submit for processing
			SubmitChapterResponse response = ingestionService.submitChapter(request);
            
			log.info("[CMD] Ingest submitted: jobId={}, chapterId={}", 
					response.getJobId(), response.getChapterId());

			return errorResponseFactory.createIngestionSuccessResponse(response.getJobId().toString());
            
		} catch (Exception e) {
			log.error("[CMD] Unexpected error during file submission: {}", file.getOriginalFilename());
			log.debug("[CMD] File submission failure details for file={}", file.getOriginalFilename(), e);
			return errorResponseFactory.createIngestionServiceError(e);
		}
	}
}
