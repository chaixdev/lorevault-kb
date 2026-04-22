package com.lorevault.api.web.command.ingestion.builder;

import com.lorevault.api.web.command.ingestion.SubmitChapterRequest;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for building SubmitChapterRequest objects and validating inputs.
 * Legacy coordinate-based construction has been removed in favor of UUID-based book targeting.
 */
@Component
public class CoordinatesBuilder {

    /**
     * Validation result for coordinate parameters
     */
    @Getter
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

    }

    // Validation helpers
    public CoordinateValidationResult validateChapterNumber(Integer chapterNumber) {
        if (chapterNumber == null || chapterNumber < 0) {
            return CoordinateValidationResult.failure("INVALID_CHAPTER_NUMBER", "Chapter number must be zero or a positive integer");
        }
        return CoordinateValidationResult.success();
    }

    /**
     * Build complete SubmitChapterRequest from file content and coordinates
     */
    public SubmitChapterRequest buildSubmitRequest(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setBookId(bookId);
        request.setChapterNumber(chapterNumber);
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
