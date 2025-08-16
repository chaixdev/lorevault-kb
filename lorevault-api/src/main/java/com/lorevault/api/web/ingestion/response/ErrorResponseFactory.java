package com.lorevault.api.web.ingestion.response;

import com.lorevault.api.web.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.ingestion.validation.FileUploadValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for creating standardized error responses.
 * Handles error formatting, status code determination, and response structure.
 * Extracted from ContentIngestionController to centralize error handling.
 */
@Component
public class ErrorResponseFactory {

    /**
     * Standard error response structure
     */
    public static class ErrorResponse {
        private final String timestamp;
        private final int status;
        private final String error;
        private final String message;
        private final String code;
        private final Map<String, Object> details;

        public ErrorResponse(int status, String error, String message, String code, Map<String, Object> details) {
            this.timestamp = LocalDateTime.now().toString();
            this.status = status;
            this.error = error;
            this.message = message;
            this.code = code;
            this.details = details != null ? details : new HashMap<>();
        }

        // Getters
        public String getTimestamp() { return timestamp; }
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public Map<String, Object> getDetails() { return details; }
    }

    /**
     * Create error response for file validation failures
     */
    public ResponseEntity<ErrorResponse> createFileValidationError(FileUploadValidator.ValidationResult result) {
        Map<String, Object> details = new HashMap<>();
        
        // Add error details if present
        if (result.getErrorDetails() != null) {
            if (result.getErrorDetails() instanceof FileUploadValidator.FileTypeError) {
                FileUploadValidator.FileTypeError error = (FileUploadValidator.FileTypeError) result.getErrorDetails();
                details.put("fileType", Map.of(
                    "received", error.getReceivedType(),
                    "supported", error.getSupportedTypes()
                ));
            } else if (result.getErrorDetails() instanceof FileUploadValidator.FileSizeError) {
                FileUploadValidator.FileSizeError error = (FileUploadValidator.FileSizeError) result.getErrorDetails();
                details.put("fileSize", Map.of(
                    "actual", error.getFileSize(),
                    "maximum", error.getMaxSize()
                ));
            }
        }

        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            result.getErrorMessage(),
            result.getErrorCode(),
            details
        );

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for coordinate validation failures
     */
    public ResponseEntity<ErrorResponse> createCoordinateValidationError(CoordinatesBuilder.CoordinateValidationResult result) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request", 
            result.getErrorMessage(),
            result.getErrorCode(),
            null
        );

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for missing file
     */
    public ResponseEntity<ErrorResponse> createMissingFileError() {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "No file provided in request",
            "MISSING_FILE",
            null
        );

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for file reading errors
     */
    public ResponseEntity<ErrorResponse> createFileReadingError(String filename, Exception cause) {
        Map<String, Object> details = new HashMap<>();
        details.put("filename", filename);
        details.put("cause", cause.getClass().getSimpleName());

        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Failed to read file content",
            "FILE_READING_ERROR",
            details
        );

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for ingestion service failures
     */
    public ResponseEntity<ErrorResponse> createIngestionServiceError(Exception cause) {
        Map<String, Object> details = new HashMap<>();
        details.put("cause", cause.getClass().getSimpleName());
        details.put("message", cause.getMessage());

        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Chapter ingestion failed due to internal error",
            "INGESTION_SERVICE_ERROR", 
            details
        );

        return ResponseEntity.internalServerError().body(error);
    }

    /**
     * Create success response for successful ingestion
     */
    public ResponseEntity<Map<String, Object>> createIngestionSuccessResponse(String jobId) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Chapter ingestion started successfully");
        response.put("jobId", jobId);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", "ACCEPTED");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Create error response for unexpected exceptions
     */
    public ResponseEntity<ErrorResponse> createUnexpectedError(Exception cause) {
        Map<String, Object> details = new HashMap<>();
        details.put("cause", cause.getClass().getSimpleName());

        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred",
            "UNEXPECTED_ERROR",
            details
        );

        return ResponseEntity.internalServerError().body(error);
    }
}
