package com.lorevault.api.web.ingestion.builder;

import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Service responsible for building PublicationCoordinates and SubmitChapterRequest objects.
 * Handles coordinate validation and request object construction.
 * Extracted from ContentIngestionController to improve single responsibility and testability.
 */
@Component
public class CoordinatesBuilder {

    /**
     * Validation result for coordinate parameters
     */
    public static class CoordinateValidationResult {
        private final boolean valid;
        private final String errorCode;
        private final String errorMessage;

        private CoordinateValidationResult(boolean valid, String errorCode, String errorMessage) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static CoordinateValidationResult success() {
            return new CoordinateValidationResult(true, null, null);
        }

        public static CoordinateValidationResult failure(String errorCode, String errorMessage) {
            return new CoordinateValidationResult(false, errorCode, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Validate coordinate parameters for required values and constraints
     */
    public CoordinateValidationResult validateCoordinates(String universe, String series, 
                                                         Integer bookNumber, Integer chapterNumber, Integer partNumber) {
        
        // Validate required universe parameter
        if (universe == null || universe.trim().isEmpty()) {
            return CoordinateValidationResult.failure("MISSING_UNIVERSE", "Universe parameter is required");
        }

        // Validate book number
        if (bookNumber == null || bookNumber < 1) {
            return CoordinateValidationResult.failure("INVALID_BOOK_NUMBER", "Book number must be a positive integer");
        }

        // Validate chapter number  
        if (chapterNumber == null || chapterNumber < 1) {
            return CoordinateValidationResult.failure("INVALID_CHAPTER_NUMBER", "Chapter number must be a positive integer");
        }

        // Validate optional part number
        if (partNumber != null && partNumber < 1) {
            return CoordinateValidationResult.failure("INVALID_PART_NUMBER", "Part number must be a positive integer if provided");
        }

        return CoordinateValidationResult.success();
    }

    /**
     * Build PublicationCoordinates from validated parameters
     */
    public PublicationCoordinates buildCoordinates(String universe, String series, 
                                                 Integer bookNumber, Integer chapterNumber) {
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse(universe.trim());
        coordinates.setSeries(series != null && !series.trim().isEmpty() ? series.trim() : null);
        coordinates.setBookNumber(bookNumber);
        coordinates.setChapterNumber(chapterNumber);
        return coordinates;
    }

    /**
     * Build complete SubmitChapterRequest from file content and coordinates
     */
    public SubmitChapterRequest buildSubmitRequest(PublicationCoordinates coordinates, 
                                                 String chapterTitle, String chapterText) {
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(coordinates);
        request.setChapterTitle(chapterTitle);
        request.setChapterText(chapterText);
        return request;
    }

    /**
     * Extract title from filename with smart formatting
     */
    public String extractTitleFromFilename(String filename) {
        if (filename == null) {
            return "Untitled Chapter";
        }
        
        // Remove extension
        String nameWithoutExt = filename.replaceFirst("\\.[^.]+$", "");
        
        // Convert kebab-case/snake_case to title case
        return Arrays.stream(nameWithoutExt.split("[-_\\s]+"))
                .map(this::capitalizeWord)
                .collect(Collectors.joining(" "));
    }

    /**
     * Determine final chapter title from provided title or filename
     */
    public String determineFinalTitle(String providedTitle, String filename) {
        return (providedTitle != null && !providedTitle.trim().isEmpty()) 
                ? providedTitle.trim() 
                : extractTitleFromFilename(filename);
    }

    /**
     * Validate title length constraints
     */
    public CoordinateValidationResult validateTitleLength(String title) {
        if (title != null && title.length() > 500) {
            return CoordinateValidationResult.failure(
                "TITLE_TOO_LONG", 
                "Title exceeds maximum length of 500 characters"
            );
        }
        return CoordinateValidationResult.success();
    }

    private String capitalizeWord(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }
}
