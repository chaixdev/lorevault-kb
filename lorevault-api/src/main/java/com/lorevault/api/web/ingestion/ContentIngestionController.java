package com.lorevault.api.web.ingestion;

import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.shared.ErrorResponse;
import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.service.ingestion.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * REST controller for content ingestion (Command operations)
 */
@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@Slf4j
public class ContentIngestionController {

    private final IngestionService ingestionService;

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

        // Validate file type
        if (!isValidFileType(file)) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("INVALID_FILE_TYPE")
                    .message("Only .txt and .md files are supported")
                    .details("supportedTypes", Arrays.asList(".txt", ".md"))
                    .details("receivedType", getFileExtension(file.getOriginalFilename()))
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }

        // Validate file size (1MB = 1048576 bytes)
        if (file.getSize() > 1048576) {
            return ResponseEntity.status(413).body(
                ErrorResponse.builder()
                    .code("FILE_TOO_LARGE")
                    .message("File exceeds maximum size limit of 1MB")
                    .details("fileSize", file.getSize())
                    .details("maxSize", 1048576)
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }

        // Validate required parameters
        if (universe == null || universe.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("MISSING_UNIVERSE")
                    .message("Universe parameter is required")
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }

        if (bookNumber == null || bookNumber < 1) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("INVALID_BOOK_NUMBER")
                    .message("Book number must be a positive integer")
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }

        if (chapterNumber == null || chapterNumber < 1) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("INVALID_CHAPTER_NUMBER")
                    .message("Chapter number must be a positive integer")
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }

        if (partNumber != null && partNumber < 1) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("INVALID_PART_NUMBER")
                    .message("Part number must be a positive integer if provided")
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }

        // Validate file is not empty
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .code("EMPTY_FILE")
                    .message("File contains no content")
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }

        try {
            // Extract content from file
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            
            // Use provided title or extract from filename
            String chapterTitle = title != null && !title.trim().isEmpty() 
                ? title.trim() 
                : extractTitleFromFilename(file.getOriginalFilename());

            // Validate title length
            if (chapterTitle.length() > 500) {
                return ResponseEntity.badRequest().body(
                    ErrorResponse.builder()
                        .code("TITLE_TOO_LONG")
                        .message("Title exceeds maximum length of 500 characters")
                        .details("titleLength", chapterTitle.length())
                        .details("maxLength", 500)
                        .timestamp(LocalDateTime.now())
                        .path("/api/ingest/submit-file")
                        .build()
                );
            }

            // Create request object
            SubmitChapterRequest request = new SubmitChapterRequest();
            
            // Create PublicationCoordinates
            PublicationCoordinates coordinates = new PublicationCoordinates();
            coordinates.setUniverse(universe.trim());
            coordinates.setSeries(series != null && !series.trim().isEmpty() ? series.trim() : null);
            coordinates.setBookNumber(bookNumber);
            coordinates.setChapterNumber(chapterNumber);
            coordinates.setPartNumber(partNumber);
            
            request.setCoordinates(coordinates);
            request.setChapterTitle(chapterTitle);
            request.setChapterText(content);

            // Submit for processing
            SubmitChapterResponse response = ingestionService.submitChapter(request);
            
            log.info("File submitted successfully: jobId={}, chapterId={}", 
                    response.getJobId(), response.getChapterId());

            return ResponseEntity.accepted().body(response);
            
        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(500).body(
                ErrorResponse.builder()
                    .code("CONTENT_EXTRACTION_FAILED")
                    .message("Unable to read file content")
                    .details("filename", file.getOriginalFilename())
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        } catch (Exception e) {
            log.error("Unexpected error during file submission: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(500).body(
                ErrorResponse.builder()
                    .code("JOB_CREATION_FAILED")
                    .message("Unable to create processing job")
                    .timestamp(LocalDateTime.now())
                    .path("/api/ingest/submit-file")
                    .build()
            );
        }
    }

    /**
     * Check if file type is supported
     */
    private boolean isValidFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) return false;
        
        String extension = getFileExtension(filename);
        return ".txt".equals(extension) || ".md".equals(extension);
    }

    /**
     * Get file extension (including the dot)
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * Extract title from filename
     */
    private String extractTitleFromFilename(String filename) {
        if (filename == null) return "Untitled Chapter";
        
        // Remove extension
        String nameWithoutExt = filename.replaceFirst("\\.[^.]+$", "");
        
        // Convert kebab-case/snake_case to title case
        return Arrays.stream(nameWithoutExt.split("[-_\\s]+"))
                .map(word -> {
                    if (word.isEmpty()) return word;
                    return word.substring(0, 1).toUpperCase() + 
                           word.substring(1).toLowerCase();
                })
                .collect(Collectors.joining(" "));
    }
}
