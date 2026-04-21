package com.lorevault.api.web.command.ingestion.extractor;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service responsible for extracting and processing file content.
 * Handles file reading, content validation, and text processing.
 * Extracted from ContentIngestionController to improve single responsibility.
 */
@Component
public class FileContentExtractor {

    /**
     * Context object for file content extraction results
     */
    @Getter
    public static class ContentExtractionResult {
        private final boolean success;
        private final String content;
        private final String filename;
        private final String errorMessage;
        private final Exception cause;

        private ContentExtractionResult(boolean success, String content, String filename, 
                                      String errorMessage, Exception cause) {
            this.success = success;
            this.content = content;
            this.filename = filename;
            this.errorMessage = errorMessage;
            this.cause = cause;
        }

        public static ContentExtractionResult success(String content, String filename) {
            return new ContentExtractionResult(true, content, filename, null, null);
        }

        public static ContentExtractionResult failure(String filename, String errorMessage, Exception cause) {
            return new ContentExtractionResult(false, null, filename, errorMessage, cause);
        }

    }

    /**
     * Extract text content from uploaded file
     */
    public ContentExtractionResult extractFileContent(MultipartFile file) {
        String filename = file.getOriginalFilename();
        
        try {
            // Read file content as UTF-8 text
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            
            // Validate content is not empty after trimming
            if (content.trim().isEmpty()) {
                return ContentExtractionResult.failure(
                    filename,
                    "File content is empty or contains only whitespace",
                    null
                );
            }

            // Normalize line endings and trim
            String normalizedContent = normalizeContent(content);

            return ContentExtractionResult.success(normalizedContent, filename);

        } catch (IOException e) {
            return ContentExtractionResult.failure(
                filename,
                "Failed to read file content: " + e.getMessage(),
                e
            );
        } catch (Exception e) {
            return ContentExtractionResult.failure(
                filename,
                "Unexpected error while processing file: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Normalize content by standardizing line endings and trimming excess whitespace
     */
    private String normalizeContent(String content) {
        return content
            .replaceAll("\\r\\n", "\n")  // Windows line endings to Unix (actual newline)
            .replaceAll("\\r", "\n")     // Mac classic line endings to Unix (actual newline)
            .trim();                      // Remove leading/trailing whitespace
    }

    /**
     * Extract filename without extension for title generation
     */
    public String extractBasename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "untitled";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return filename;
        }
        
        return filename.substring(0, lastDotIndex);
    }

    /**
     * Validate content meets minimum length requirements
     */
    public boolean validateContentLength(String content) {
        return content != null && content.trim().length() >= 10; // Minimum 10 characters
    }

    /**
     * Get content preview for logging/debugging (first 100 characters)
     */
    public String getContentPreview(String content) {
        if (content == null || content.isEmpty()) {
            return "[empty]";
        }
        
        String preview = content.trim();
        if (preview.length() > 100) {
            return preview.substring(0, 100) + "...";
        }
        
        return preview;
    }
}
